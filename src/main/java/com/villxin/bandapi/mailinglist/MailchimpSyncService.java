package com.villxin.bandapi.mailinglist;

import com.villxin.bandapi.entity.Subscriber;
import com.villxin.bandapi.entity.Subscriber.MailchimpStatus;
import com.villxin.bandapi.entity.Subscriber.Source;
import com.villxin.bandapi.exception.ApiException;
import com.villxin.bandapi.repository.SubscriberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Copies the mailing list from Postgres into Mailchimp.
 *
 * <p>Postgres is written first and stays the source of truth; Mailchimp is only
 * where campaigns are sent from. A new signup is pushed inline (best effort);
 * anything that didn't make it stays PENDING until the owner presses sync in the
 * admin console. There is deliberately no background timer: App Runner throttles
 * CPU between requests, so scheduled jobs there are unreliable.</p>
 *
 * <p>Log lines carry subscriber ids, never email addresses.</p>
 */
@Service
public class MailchimpSyncService {

    private static final Logger log = LoggerFactory.getLogger(MailchimpSyncService.class);

    /** Tag on everyone who joined when the list promised "the first release, and nothing else". */
    public static final String LEGACY_TAG = "legacy-first-release";

    private static final int MAX_REJECTIONS_REPORTED = 20;
    private static final int ERROR_COLUMN_LENGTH = 500;

    enum Outcome { SYNCED, REJECTED, RETRY_LATER }

    /**
     * @param synced     now in Mailchimp
     * @param rejected   Mailchimp refused the address; won't be retried
     * @param retryLater temporary failure (timeout, rate limit, Mailchimp 5xx)
     * @param rejections "email: reason" for this run's rejections (admin-only response)
     * @param counts     per-status totals after the run
     */
    public record SyncResult(int synced, int rejected, int retryLater,
                             List<String> rejections, Map<MailchimpStatus, Long> counts) {}

    public record Status(boolean enabled, Map<MailchimpStatus, Long> counts, long total) {}

    private final MailchimpClient client;
    private final SubscriberRepository repository;
    private final Clock clock;

    @Autowired
    public MailchimpSyncService(MailchimpClient client, SubscriberRepository repository) {
        this(client, repository, Clock.systemUTC());
    }

    MailchimpSyncService(MailchimpClient client, SubscriberRepository repository, Clock clock) {
        this.client = client;
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Best-effort push of a brand-new signup. Never throws: the fan's signup is
     * already saved, and a Mailchimp problem must not turn it into an error page.
     */
    public void trySyncNewSignup(Subscriber subscriber) {
        if (!client.isConfigured()) {
            return;
        }
        try {
            syncOne(subscriber);
        } catch (ApiException e) {
            log.error("Mailchimp sync of subscriber {} failed: {}", subscriber.getId(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("Mailchimp sync of subscriber {} failed unexpectedly", subscriber.getId(), e);
        }
    }

    /**
     * Admin-triggered: retries every PENDING row, and with {@code includeLegacy}
     * also imports the pre-Mailchimp list (tagged {@value #LEGACY_TAG}).
     * Stops at the first credentials/audience error — every row would fail the same way.
     */
    public SyncResult syncPending(boolean includeLegacy) {
        if (!client.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MAILCHIMP_DISABLED",
                    "Mailchimp isn't configured — set MAILCHIMP_API_KEY and MAILCHIMP_AUDIENCE_ID.");
        }

        List<MailchimpStatus> wanted = includeLegacy
                ? List.of(MailchimpStatus.PENDING, MailchimpStatus.LEGACY)
                : List.of(MailchimpStatus.PENDING);

        int synced = 0;
        int rejected = 0;
        int retryLater = 0;
        List<String> rejections = new ArrayList<>();

        for (Subscriber subscriber : repository.findByMailchimpStatusInOrderByIdAsc(wanted)) {
            switch (syncOne(subscriber)) {
                case SYNCED -> synced++;
                case REJECTED -> {
                    rejected++;
                    if (rejections.size() < MAX_REJECTIONS_REPORTED) {
                        rejections.add(subscriber.getEmail() + ": " + subscriber.getMailchimpError());
                    }
                }
                case RETRY_LATER -> retryLater++;
            }
        }
        log.info("Mailchimp sync (includeLegacy={}): synced={} rejected={} retryLater={}",
                includeLegacy, synced, rejected, retryLater);
        return new SyncResult(synced, rejected, retryLater, rejections, counts());
    }

    public Status status() {
        Map<MailchimpStatus, Long> counts = counts();
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new Status(client.isConfigured(), counts, total);
    }

    Outcome syncOne(Subscriber subscriber) {
        try {
            client.upsertSubscribed(subscriber.getEmail());
            if (subscriber.getSource() == Source.LEGACY) {
                client.addTags(subscriber.getEmail(), List.of(LEGACY_TAG));
            }
            subscriber.setMailchimpStatus(MailchimpStatus.SYNCED);
            subscriber.setMailchimpSyncedAt(Instant.now(clock));
            subscriber.setMailchimpError(null);
            repository.save(subscriber);
            return Outcome.SYNCED;
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 401 || code == 403 || code == 404) {
                // Bad key or wrong audience id: a config problem, not this address's fault.
                // Leave the row untouched so it syncs once the setting is fixed.
                throw new ApiException(HttpStatus.BAD_GATEWAY, "MAILCHIMP_CONFIG",
                        "Mailchimp rejected the API key or audience id (" + MailchimpClient.describe(e)
                                + "). Check MAILCHIMP_API_KEY and MAILCHIMP_AUDIENCE_ID.");
            }
            if (code == 400) {
                // Address-specific refusal (looks fake, "Member In Compliance State", ...).
                // Retrying would get the same answer, so park it for a human.
                subscriber.setMailchimpStatus(MailchimpStatus.REJECTED);
                subscriber.setMailchimpError(truncate(MailchimpClient.describe(e)));
                repository.save(subscriber);
                log.warn("Mailchimp rejected subscriber {}: {}", subscriber.getId(), subscriber.getMailchimpError());
                return Outcome.REJECTED;
            }
            // 429 / 5xx: temporary — keep the current status and record why.
            return recordRetry(subscriber, MailchimpClient.describe(e));
        } catch (ResourceAccessException e) {
            // Timeout or network failure.
            return recordRetry(subscriber, "Mailchimp unreachable: " + e.getMessage());
        }
    }

    private Outcome recordRetry(Subscriber subscriber, String reason) {
        subscriber.setMailchimpError(truncate(reason));
        repository.save(subscriber);
        log.warn("Mailchimp sync of subscriber {} will be retried: {}", subscriber.getId(), reason);
        return Outcome.RETRY_LATER;
    }

    private Map<MailchimpStatus, Long> counts() {
        Map<MailchimpStatus, Long> counts = new EnumMap<>(MailchimpStatus.class);
        for (MailchimpStatus status : MailchimpStatus.values()) {
            counts.put(status, repository.countByMailchimpStatus(status));
        }
        return counts;
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= ERROR_COLUMN_LENGTH ? s : s.substring(0, ERROR_COLUMN_LENGTH);
    }
}

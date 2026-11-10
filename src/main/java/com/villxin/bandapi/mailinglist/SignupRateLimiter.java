package com.villxin.bandapi.mailinglist;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Fixed-window limits on mailing-list signups, in memory.
 *
 * <p>Why it matters: the list is single opt-in on Mailchimp's free plan (250
 * contacts). Every fake signup costs a contact slot and gets emailed by the
 * next campaign, and going over the limit holds all sending.</p>
 *
 * <p>Two layers. Per client (by IP) stops a single noisy bot. The client IP
 * comes from X-Forwarded-For, which a caller can forge, so there is also a
 * site-wide cap per window that no header can get around — at the cost of
 * briefly turning away real fans during a flood.</p>
 *
 * <p>State is per instance and resets on deploy. That's fine at this size; a
 * second App Runner instance would just double the effective limits.</p>
 */
@Component
public class SignupRateLimiter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final int perClientLimit;
    private final Duration perClientWindow;
    private final int globalLimit;
    private final Duration globalWindow;
    private final Clock clock;

    private final Map<String, Window> clients = new HashMap<>();
    private Window global;

    @Autowired
    public SignupRateLimiter(@Value("${mailing-list.rate-limit.per-client:5}") int perClientLimit,
                             @Value("${mailing-list.rate-limit.per-client-window-minutes:10}") long perClientWindowMinutes,
                             @Value("${mailing-list.rate-limit.global:60}") int globalLimit,
                             @Value("${mailing-list.rate-limit.global-window-minutes:60}") long globalWindowMinutes) {
        this(perClientLimit, Duration.ofMinutes(perClientWindowMinutes),
                globalLimit, Duration.ofMinutes(globalWindowMinutes), Clock.systemUTC());
    }

    SignupRateLimiter(int perClientLimit, Duration perClientWindow,
                      int globalLimit, Duration globalWindow, Clock clock) {
        this.perClientLimit = perClientLimit;
        this.perClientWindow = perClientWindow;
        this.globalLimit = globalLimit;
        this.globalWindow = globalWindow;
        this.clock = clock;
    }

    /**
     * Records an attempt and says whether it may proceed. A refused attempt is
     * not counted, so a blocked bot doesn't eat site-wide slots meant for fans.
     */
    public synchronized boolean tryAcquire(String clientKey) {
        Instant now = clock.instant();

        if (global == null || global.expired(now, globalWindow)) {
            global = new Window(now);
        }
        Window client = clients.get(clientKey);
        if (client == null || client.expired(now, perClientWindow)) {
            if (clients.size() >= MAX_TRACKED_CLIENTS) {
                clients.values().removeIf(w -> w.expired(now, perClientWindow));
            }
            client = new Window(now);
            clients.put(clientKey, client);
        }

        if (client.count >= perClientLimit || global.count >= globalLimit) {
            return false;
        }
        client.count++;
        global.count++;
        return true;
    }

    /** Clears all windows (tests share one Spring context). */
    public synchronized void reset() {
        clients.clear();
        global = null;
    }

    private static final class Window {
        private final Instant start;
        private int count;

        private Window(Instant start) {
            this.start = start;
        }

        private boolean expired(Instant now, Duration length) {
            return !now.isBefore(start.plus(length));
        }
    }
}

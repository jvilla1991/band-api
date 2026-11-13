package com.villxin.bandapi.mailinglist;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Thin client for the Mailchimp Marketing API (v3) — just the two calls the
 * mailing list needs: add-or-update a member, and tag a member.
 *
 * <p>Off unless both {@code MAILCHIMP_API_KEY} and {@code MAILCHIMP_AUDIENCE_ID}
 * are set, so the service deploys safely before the secret exists. The server
 * prefix (e.g. {@code us5}) is the suffix of the key itself, so there is no
 * separate setting for it.</p>
 */
@Component
public class MailchimpClient {

    private static final Logger log = LoggerFactory.getLogger(MailchimpClient.class);

    /** Mailchimp data-center prefixes look like "us5" — anything else is a mangled key. */
    private static final Pattern SERVER_PREFIX = Pattern.compile("^[a-z]{2,}\\d+$");

    // Signup calls Mailchimp inline, so a slow Mailchimp must not hold the fan's
    // request hostage: worst case ~5s, then the row stays PENDING for retry.
    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int READ_TIMEOUT_MS = 3_000;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient restClient; // null when not configured
    private final String audienceId;

    @Autowired
    public MailchimpClient(@Value("${mailchimp.api-key:}") String apiKey,
                           @Value("${mailchimp.audience-id:}") String audienceId) {
        this(RestClient.builder().requestFactory(timeouts()), apiKey, audienceId);
    }

    /** Visible for tests: lets a mock server bind to the builder. */
    MailchimpClient(RestClient.Builder builder, String apiKey, String audienceId) {
        String key = apiKey == null ? "" : apiKey.trim();
        this.audienceId = audienceId == null ? "" : audienceId.trim();

        String prefix = serverPrefix(key);
        if (key.isEmpty() || this.audienceId.isEmpty()) {
            this.restClient = null;
            log.info("Mailchimp sync is off (MAILCHIMP_API_KEY / MAILCHIMP_AUDIENCE_ID not set)");
        } else if (prefix == null) {
            this.restClient = null;
            log.error("Mailchimp sync is off: MAILCHIMP_API_KEY doesn't end in a server prefix like '-us5'");
        } else {
            this.restClient = builder
                    .baseUrl("https://" + prefix + ".api.mailchimp.com/3.0")
                    // Mailchimp ignores the username; the key is the password.
                    .defaultHeaders(h -> h.setBasicAuth("villxin-band-api", key))
                    .defaultHeader("User-Agent", "villxin-band-api/1.0")
                    .build();
        }
    }

    public boolean isConfigured() {
        return restClient != null;
    }

    /**
     * Adds the address as subscribed, or leaves an existing member exactly as
     * they are. Only {@code status_if_new} is sent — never {@code status} — so
     * someone who unsubscribed in Mailchimp is not re-subscribed when the same
     * address comes through again (a retry, a re-signup, the legacy import).
     */
    public void upsertSubscribed(String email) {
        requireConfigured();
        restClient.put()
                .uri("/lists/{listId}/members/{hash}", audienceId, subscriberHash(email))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UpsertMember(email, "subscribed"))
                .retrieve()
                .toBodilessEntity();
    }

    /** Applies tags to an existing member (Mailchimp creates unknown tags on the fly). */
    public void addTags(String email, List<String> tags) {
        requireConfigured();
        restClient.post()
                .uri("/lists/{listId}/members/{hash}/tags", audienceId, subscriberHash(email))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TagUpdate(tags.stream().map(t -> new Tag(t, "active")).toList()))
                .retrieve()
                .toBodilessEntity();
    }

    /** Mailchimp addresses a member by the MD5 of the lowercased email. */
    static String subscriberHash(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(email.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is always available on the JVM", e);
        }
    }

    /** "title: detail" from Mailchimp's error body, falling back to the HTTP status text. */
    static String describe(RestClientResponseException e) {
        try {
            JsonNode body = JSON.readTree(e.getResponseBodyAsString());
            String title = body.path("title").asText("");
            String detail = body.path("detail").asText("");
            if (!title.isEmpty() || !detail.isEmpty()) {
                return detail.isEmpty() ? title : title + ": " + detail;
            }
        } catch (Exception ignored) {
            // not JSON — fall through
        }
        return e.getStatusCode().value() + " " + e.getStatusText();
    }

    static String serverPrefix(String apiKey) {
        int dash = apiKey.lastIndexOf('-');
        if (dash < 0 || dash == apiKey.length() - 1) {
            return null;
        }
        String prefix = apiKey.substring(dash + 1);
        return SERVER_PREFIX.matcher(prefix).matches() ? prefix : null;
    }

    private void requireConfigured() {
        if (restClient == null) {
            throw new IllegalStateException("Mailchimp is not configured");
        }
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return factory;
    }

    private record UpsertMember(
            @JsonProperty("email_address") String emailAddress,
            @JsonProperty("status_if_new") String statusIfNew) {}

    private record TagUpdate(List<Tag> tags) {}

    private record Tag(String name, String status) {}
}

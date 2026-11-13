package com.villxin.bandapi.controller;

import com.villxin.bandapi.entity.Subscriber;
import com.villxin.bandapi.mailinglist.MailchimpSyncService;
import com.villxin.bandapi.mailinglist.SignupRateLimiter;
import com.villxin.bandapi.repository.SubscriberRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
public class SubscribeController {

    private static final Logger log = LoggerFactory.getLogger(SubscribeController.class);

    // Response contract (per the villxin design handoff):
    //   { ok: true, message } | { ok: false, code: "duplicate" | "invalid" | "rate_limited", message }
    private static final Pattern EMAIL_RX = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final Map<String, Object> SUCCESS = Map.of("ok", true, "message", "Subscribed!");

    private final SubscriberRepository repository;
    private final MailchimpSyncService mailchimpSyncService;
    private final SignupRateLimiter rateLimiter;

    public SubscribeController(SubscriberRepository repository,
                               MailchimpSyncService mailchimpSyncService,
                               SignupRateLimiter rateLimiter) {
        this.repository = repository;
        this.mailchimpSyncService = mailchimpSyncService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, Object>> subscribe(@RequestBody SubscribeRequest request,
                                                         HttpServletRequest http) {
        // Honeypot: the form has a field real visitors never see. Anything typed
        // into it is a bot — answer with the normal success so it doesn't adapt,
        // but store nothing.
        if (request.website() != null && !request.website().isBlank()) {
            log.info("Mailing-list honeypot tripped; signup dropped");
            return ResponseEntity.ok(SUCCESS);
        }

        if (!rateLimiter.tryAcquire(clientKey(http))) {
            return ResponseEntity.status(429)
                    .body(Map.of("ok", false, "code", "rate_limited",
                            "message", "Too many signups right now — try again in a few minutes."));
        }

        String email = request.email() == null ? "" : request.email().toLowerCase().trim();

        // Validated manually (not bean validation) so failures render the spec's
        // { ok, code, message } shape instead of the global { errors } shape.
        if (!EMAIL_RX.matcher(email).matches()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "code", "invalid", "message", "Invalid email address"));
        }

        if (repository.existsByEmail(email)) {
            return duplicate();
        }

        Subscriber subscriber = new Subscriber();
        subscriber.setEmail(email);
        Subscriber saved;
        try {
            saved = repository.save(subscriber);
        } catch (DataIntegrityViolationException e) {
            return duplicate();
        }

        // Saved first, so the signup counts even if Mailchimp is down; this
        // never throws, and anything it can't send stays PENDING for retry.
        mailchimpSyncService.trySyncNewSignup(saved);

        return ResponseEntity.ok(SUCCESS);
    }

    private static ResponseEntity<Map<String, Object>> duplicate() {
        return ResponseEntity.status(409)
                .body(Map.of("ok", false, "code", "duplicate", "message", "Already subscribed"));
    }

    /**
     * Best-effort client identity: the first X-Forwarded-For hop (App Runner sits
     * behind a proxy, so the socket address is the proxy's). Forgeable, which is
     * why {@link SignupRateLimiter} also has a site-wide cap.
     */
    private static String clientKey(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }

    /** {@code website} is the honeypot — the real form always sends it empty. */
    record SubscribeRequest(String email, String website) {}
}

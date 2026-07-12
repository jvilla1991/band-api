package com.villxin.bandapi.controller;

import com.villxin.bandapi.entity.Subscriber;
import com.villxin.bandapi.repository.SubscriberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
public class SubscribeController {

    // Response contract (per the villxin design handoff):
    //   { ok: true, message } | { ok: false, code: "duplicate" | "invalid", message }
    private static final Pattern EMAIL_RX = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final SubscriberRepository repository;

    public SubscribeController(SubscriberRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, Object>> subscribe(@RequestBody SubscribeRequest request) {
        String email = request.email() == null ? "" : request.email().toLowerCase().trim();

        // Validated manually (not bean validation) so failures render the spec's
        // { ok, code, message } shape instead of the global { errors } shape.
        if (!EMAIL_RX.matcher(email).matches()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "code", "invalid", "message", "Invalid email address"));
        }

        if (repository.existsByEmail(email)) {
            return ResponseEntity.status(409)
                    .body(Map.of("ok", false, "code", "duplicate", "message", "Already subscribed"));
        }

        Subscriber subscriber = new Subscriber();
        subscriber.setEmail(email);
        try {
            repository.save(subscriber);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(409)
                    .body(Map.of("ok", false, "code", "duplicate", "message", "Already subscribed"));
        }

        return ResponseEntity.ok(Map.of("ok", true, "message", "Subscribed!"));
    }

    record SubscribeRequest(String email) {}
}

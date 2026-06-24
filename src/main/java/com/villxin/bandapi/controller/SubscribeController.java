package com.villxin.bandapi.controller;

import com.villxin.bandapi.entity.Subscriber;
import com.villxin.bandapi.repository.SubscriberRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SubscribeController {

    private final SubscriberRepository repository;

    public SubscribeController(SubscriberRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, String>> subscribe(@Valid @RequestBody SubscribeRequest request) {
        Subscriber subscriber = new Subscriber();
        subscriber.setEmail(request.email().toLowerCase().trim());

        try {
            repository.save(subscriber);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(409).body(Map.of("error", "Already subscribed"));
        }

        return ResponseEntity.ok(Map.of("message", "Subscribed!"));
    }

    record SubscribeRequest(
        @Email(message = "Invalid email address")
        @NotBlank(message = "Email is required")
        String email
    ) {}
}

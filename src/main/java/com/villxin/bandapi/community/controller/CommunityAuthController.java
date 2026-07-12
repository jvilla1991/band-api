package com.villxin.bandapi.community.controller;

import com.villxin.bandapi.community.dto.CommunityDtos.ProfileDto;
import com.villxin.bandapi.community.service.CommunityUserService;
import com.villxin.bandapi.community.service.MagicLinkAuthService;
import com.villxin.bandapi.community.service.ProfileService;
import com.villxin.bandapi.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Passwordless (magic-link) auth for YourArea members. */
@RestController
@RequestMapping("/api/community/auth")
public class CommunityAuthController {

    private final MagicLinkAuthService magicLink;
    private final CommunityUserService users;
    private final ProfileService profileService;

    public CommunityAuthController(MagicLinkAuthService magicLink,
                                   CommunityUserService users,
                                   ProfileService profileService) {
        this.magicLink = magicLink;
        this.users = users;
        this.profileService = profileService;
    }

    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(@Valid @RequestBody SignupRequest request) {
        magicLink.signup(request.email(), request.username());
        return ResponseEntity.ok(Map.of("ok", true,
                "message", "Check your email for a verification link"));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        magicLink.login(request.email());
        return ResponseEntity.ok(Map.of("ok", true,
                "message", "Check your email for a login link"));
    }

    /** Exchanges the emailed one-time token for a session JWT. */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(@Valid @RequestBody VerifyRequest request) {
        MagicLinkAuthService.Session session = magicLink.verify(request.token());
        User user = session.user();
        return ResponseEntity.ok(Map.of(
                "token", session.token(),
                "username", user.getUsername(),
                "displayName", user.getDisplayName(),
                "official", user.isOfficial()
        ));
    }

    /** The signed-in member's own profile (plus email, which is never public). */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication auth) {
        User user = users.requireMember(auth);
        ProfileDto profile = profileService.toDto(user);
        return ResponseEntity.ok(Map.of("email", user.getEmail(), "profile", profile));
    }

    record SignupRequest(
            @Email(message = "Invalid email address")
            @NotBlank(message = "Email is required")
            String email,
            @NotBlank(message = "Username is required")
            String username
    ) {}

    record LoginRequest(
            @Email(message = "Invalid email address")
            @NotBlank(message = "Email is required")
            String email
    ) {}

    record VerifyRequest(@NotBlank(message = "Token is required") String token) {}
}

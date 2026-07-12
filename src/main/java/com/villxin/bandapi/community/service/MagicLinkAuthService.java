package com.villxin.bandapi.community.service;

import com.villxin.bandapi.community.entity.MagicLinkToken;
import com.villxin.bandapi.community.repository.MagicLinkTokenRepository;
import com.villxin.bandapi.entity.User;
import com.villxin.bandapi.exception.ApiException;
import com.villxin.bandapi.repository.UserRepository;
import com.villxin.bandapi.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Passwordless auth: signup/login issue a one-time emailed magic link;
 * exchanging (verifying) the link mints a session JWT via the existing
 * {@link JwtUtil}. Tokens are stored SHA-256-hashed, are single-use, and
 * expire after {@code community.magic-link.ttl-minutes}.
 */
@Service
public class MagicLinkAuthService {

    private static final Pattern USERNAME_RX = Pattern.compile("^[a-z0-9_]{3,24}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MagicLinkTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final JwtUtil jwtUtil;
    private final String baseUrl;
    private final Duration ttl;

    public MagicLinkAuthService(MagicLinkTokenRepository tokenRepository,
                                UserRepository userRepository,
                                EmailSender emailSender,
                                JwtUtil jwtUtil,
                                @Value("${community.magic-link.base-url}") String baseUrl,
                                @Value("${community.magic-link.ttl-minutes:15}") long ttlMinutes) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.jwtUtil = jwtUtil;
        this.baseUrl = baseUrl;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    /** Result of a successful magic-link exchange. */
    public record Session(String token, User user) {}

    /** Signup step 1: validate email+username, email a verification link. */
    @Transactional
    public void signup(String rawEmail, String rawUsername) {
        String email = normalizeEmail(rawEmail);
        String username = rawUsername == null ? "" : rawUsername.toLowerCase().trim();

        if (!USERNAME_RX.matcher(username).matches()) {
            throw ApiException.badRequest("USERNAME_INVALID",
                    "Username must be 3-24 characters: lowercase letters, digits, underscore");
        }
        if (userRepository.existsByUsername(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "That username is already taken");
        }
        userRepository.findByEmail(email).ifPresent(existing -> {
            if (existing.getUsername() != null) {
                throw ApiException.conflict("EMAIL_TAKEN", "That email already has an account — log in instead");
            }
        });

        String link = issueToken(email, username, MagicLinkToken.Purpose.SIGNUP);
        emailSender.send(email, "Verify your YourArea account",
                "Welcome to YourArea. Click to verify your email and claim '" + username + "':\n" + link
                        + "\n\nThis link is single-use and expires in " + ttl.toMinutes() + " minutes.");
    }

    /** Login step 1: email a magic link to an existing member. */
    @Transactional
    public void login(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        User user = userRepository.findByEmail(email)
                .filter(u -> u.getUsername() != null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NO_ACCOUNT",
                        "No YourArea account for that email — sign up first"));

        String link = issueToken(user.getEmail(), null, MagicLinkToken.Purpose.LOGIN);
        emailSender.send(user.getEmail(), "Your YourArea login link",
                "Click to log in to YourArea:\n" + link
                        + "\n\nThis link is single-use and expires in " + ttl.toMinutes() + " minutes.");
    }

    /** Step 2 (both flows): exchange the one-time token for a session JWT. */
    @Transactional
    public Session verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.badRequest("LINK_INVALID", "That link is not valid");
        }
        MagicLinkToken token = tokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> ApiException.badRequest("LINK_INVALID", "That link is not valid"));

        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("LINK_EXPIRED", "That link has expired — request a new one");
        }
        token.setUsedAt(Instant.now());
        tokenRepository.save(token);

        User user = (token.getPurpose() == MagicLinkToken.Purpose.SIGNUP)
                ? completeSignup(token)
                : userRepository.findByEmail(token.getEmail())
                        .orElseThrow(() -> ApiException.badRequest("LINK_INVALID", "That link is not valid"));

        return new Session(jwtUtil.generate(user.getEmail(), user.getRole()), user);
    }

    /** The username is claimed here — only after the email is verified. */
    private User completeSignup(MagicLinkToken token) {
        User existing = userRepository.findByEmail(token.getEmail()).orElse(null);
        if (existing != null && existing.getUsername() != null) {
            return existing; // double-click / re-signup with same email: treat as login
        }
        if (userRepository.existsByUsername(token.getUsername())) {
            throw ApiException.conflict("USERNAME_TAKEN",
                    "That username was claimed while your link was in flight — sign up again");
        }
        User user = existing != null ? existing : new User();
        user.setEmail(token.getEmail());
        user.setUsername(token.getUsername());
        user.setDisplayName(token.getUsername());
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    /** Creates + stores a hashed one-time token; returns the emailed link (never logged here). */
    private String issueToken(String email, String username, MagicLinkToken.Purpose purpose) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        MagicLinkToken token = new MagicLinkToken();
        token.setTokenHash(sha256(raw));
        token.setEmail(email);
        token.setUsername(username);
        token.setPurpose(purpose);
        token.setExpiresAt(Instant.now().plus(ttl));
        tokenRepository.save(token);

        // hash route — the frontend is a hash-routed SPA on a static host, so
        // the token must survive without any server-side rewrite
        return baseUrl + "/#/yourarea/verify?token=" + raw;
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.toLowerCase().trim();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

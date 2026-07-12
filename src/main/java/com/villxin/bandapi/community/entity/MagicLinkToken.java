package com.villxin.bandapi.community.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One-time magic-link token. Only the SHA-256 hash of the token is stored;
 * the raw token exists solely inside the emailed link.
 */
@Entity
@Table(name = "magic_link_tokens")
public class MagicLinkToken {

    public enum Purpose { SIGNUP, LOGIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", unique = true, nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 255)
    private String email;

    // username being claimed — only for SIGNUP tokens
    @Column(length = 24)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Purpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Purpose getPurpose() { return purpose; }
    public void setPurpose(Purpose purpose) { this.purpose = purpose; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }
    public Instant getCreatedAt() { return createdAt; }
}

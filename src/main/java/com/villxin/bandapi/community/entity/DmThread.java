package com.villxin.bandapi.community.entity;

import com.villxin.bandapi.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * A two-way DM thread between exactly two members. One thread per pair —
 * the pair is normalized so userA.id &lt; userB.id. REVOKED = one side
 * "revoked access" (block); only the revoker can reopen it.
 */
@Entity
@Table(name = "dm_threads", uniqueConstraints =
        @UniqueConstraint(columnNames = {"user_a_id", "user_b_id"}))
public class DmThread {

    public enum Status { OPEN, REVOKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_a_id", nullable = false)
    private User userA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_b_id", nullable = false)
    private User userB;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by_id")
    private User revokedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    /** True if the given user is one of the two participants. */
    public boolean hasParticipant(Long userId) {
        return userA.getId().equals(userId) || userB.getId().equals(userId);
    }

    /** The participant that is not the given user. */
    public User otherParticipant(Long userId) {
        return userA.getId().equals(userId) ? userB : userA;
    }

    public Long getId() { return id; }
    public User getUserA() { return userA; }
    public void setUserA(User userA) { this.userA = userA; }
    public User getUserB() { return userB; }
    public void setUserB(User userB) { this.userB = userB; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public User getRevokedBy() { return revokedBy; }
    public void setRevokedBy(User revokedBy) { this.revokedBy = revokedBy; }
    public Instant getCreatedAt() { return createdAt; }
}

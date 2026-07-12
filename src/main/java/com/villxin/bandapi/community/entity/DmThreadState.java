package com.villxin.bandapi.community.entity;

import com.villxin.bandapi.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * Per-user, per-thread state: last-read marker (drives unread counts)
 * and trash flag (soft delete — hides the thread for this user only).
 */
@Entity
@Table(name = "dm_thread_states", uniqueConstraints =
        @UniqueConstraint(columnNames = {"thread_id", "user_id"}))
public class DmThreadState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thread_id", nullable = false)
    private DmThread thread;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(nullable = false)
    private boolean trashed = false;

    public Long getId() { return id; }
    public DmThread getThread() { return thread; }
    public void setThread(DmThread thread) { this.thread = thread; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Instant getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(Instant lastReadAt) { this.lastReadAt = lastReadAt; }
    public boolean isTrashed() { return trashed; }
    public void setTrashed(boolean trashed) { this.trashed = trashed; }
}

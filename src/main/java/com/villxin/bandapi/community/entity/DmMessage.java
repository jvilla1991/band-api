package com.villxin.bandapi.community.entity;

import com.villxin.bandapi.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "dm_messages")
public class DmMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thread_id", nullable = false)
    private DmThread thread;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public DmThread getThread() { return thread; }
    public void setThread(DmThread thread) { this.thread = thread; }
    public User getSender() { return sender; }
    public void setSender(User sender) { this.sender = sender; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Instant getCreatedAt() { return createdAt; }
}

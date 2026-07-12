package com.villxin.bandapi.community.entity;

import com.villxin.bandapi.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

/** MySpace-style comment left on another member's profile wall. */
@Entity
@Table(name = "wall_comments")
public class WallComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_user_id", nullable = false)
    private User profileUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private boolean glitter = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getProfileUser() { return profileUser; }
    public void setProfileUser(User profileUser) { this.profileUser = profileUser; }
    public User getAuthor() { return author; }
    public void setAuthor(User author) { this.author = author; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public boolean isGlitter() { return glitter; }
    public void setGlitter(boolean glitter) { this.glitter = glitter; }
    public Instant getCreatedAt() { return createdAt; }
}

package com.villxin.bandapi.community.entity;

import com.villxin.bandapi.entity.User;
import jakarta.persistence.*;

/** One slot in a member's ordered Top 8 friends grid. */
@Entity
@Table(name = "top_friends", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "position"}),
        @UniqueConstraint(columnNames = {"user_id", "friend_id"})
})
public class TopFriend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "friend_id", nullable = false)
    private User friend;

    @Column(nullable = false)
    private int position;

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public User getFriend() { return friend; }
    public void setFriend(User friend) { this.friend = friend; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}

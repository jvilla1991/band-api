package com.villxin.bandapi.entity;

import com.villxin.bandapi.community.entity.ThemeAccent;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Email
    @NotBlank
    @Column(unique = true, nullable = false, length = 255)
    private String email;

    // null for passwordless (magic-link) community members
    @Column
    private String password;

    @Column(nullable = false, length = 20)
    private String role = "USER";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // --- YourArea community fields (null username = not a community member) ---

    @Column(unique = true, length = 24)
    private String username;

    // Official villxin account flag — settable only via seed/DB, never via API
    @Column(nullable = false)
    private boolean official = false;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(columnDefinition = "TEXT")
    private String about;

    @Column(name = "who_to_meet", columnDefinition = "TEXT")
    private String whoToMeet;

    @Column(length = 100)
    private String mood;

    // free-text reference to one of the site's demo tracks (id or title)
    @Column(name = "profile_song", length = 200)
    private String profileSong;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_accent", nullable = false, length = 20)
    private ThemeAccent themeAccent = ThemeAccent.EMBER;

    @Column(name = "theme_glitter", nullable = false)
    private boolean themeGlitter = false;

    @Column(name = "theme_tiled_bg", nullable = false)
    private boolean themeTiledBg = false;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public boolean isOfficial() { return official; }
    public void setOfficial(boolean official) { this.official = official; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about; }
    public String getWhoToMeet() { return whoToMeet; }
    public void setWhoToMeet(String whoToMeet) { this.whoToMeet = whoToMeet; }
    public String getMood() { return mood; }
    public void setMood(String mood) { this.mood = mood; }
    public String getProfileSong() { return profileSong; }
    public void setProfileSong(String profileSong) { this.profileSong = profileSong; }
    public ThemeAccent getThemeAccent() { return themeAccent; }
    public void setThemeAccent(ThemeAccent themeAccent) { this.themeAccent = themeAccent; }
    public boolean isThemeGlitter() { return themeGlitter; }
    public void setThemeGlitter(boolean themeGlitter) { this.themeGlitter = themeGlitter; }
    public boolean isThemeTiledBg() { return themeTiledBg; }
    public void setThemeTiledBg(boolean themeTiledBg) { this.themeTiledBg = themeTiledBg; }
}

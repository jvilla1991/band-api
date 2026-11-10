package com.villxin.bandapi.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

@Entity
@Table(name = "subscribers")
public class Subscriber {

    /** How the address got onto the list (see V9 migration). */
    public enum Source { SITE, LEGACY }

    /** Where the address stands with Mailchimp (see V9 migration). */
    public enum MailchimpStatus { PENDING, LEGACY, SYNCED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Email
    @NotBlank
    @Column(unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source = Source.SITE;

    @Enumerated(EnumType.STRING)
    @Column(name = "mailchimp_status", nullable = false, length = 20)
    private MailchimpStatus mailchimpStatus = MailchimpStatus.PENDING;

    @Column(name = "mailchimp_synced_at")
    private Instant mailchimpSyncedAt;

    @Column(name = "mailchimp_error", length = 500)
    private String mailchimpError;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Instant getCreatedAt() { return createdAt; }
    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }
    public MailchimpStatus getMailchimpStatus() { return mailchimpStatus; }
    public void setMailchimpStatus(MailchimpStatus mailchimpStatus) { this.mailchimpStatus = mailchimpStatus; }
    public Instant getMailchimpSyncedAt() { return mailchimpSyncedAt; }
    public void setMailchimpSyncedAt(Instant mailchimpSyncedAt) { this.mailchimpSyncedAt = mailchimpSyncedAt; }
    public String getMailchimpError() { return mailchimpError; }
    public void setMailchimpError(String mailchimpError) { this.mailchimpError = mailchimpError; }
}

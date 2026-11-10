package com.villxin.bandapi.site.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One photo in the owner-managed "About" carousel. The image bytes live inline
 * in {@code image_data} (bytea) alongside their {@code content_type}; the public
 * API hands out a URL to the bytes endpoint rather than the bytes themselves in
 * the list. {@code focus} is a nullable CSS object-position string (e.g.
 * {@code "center 62%"}) the frontend applies as a focal point.
 */
@Entity
@Table(name = "about_photos")
public class AboutPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String alt;

    @Column(columnDefinition = "TEXT")
    private String focus;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "content_type", nullable = false, columnDefinition = "TEXT")
    private String contentType;

    // byte[] maps to Postgres bytea by default in Hibernate 6 (no @Lob, which
    // would route through the large-object/oid API instead).
    @Column(name = "image_data", nullable = false, columnDefinition = "bytea")
    private byte[] imageData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getAlt() { return alt; }
    public void setAlt(String alt) { this.alt = alt; }
    public String getFocus() { return focus; }
    public void setFocus(String focus) { this.focus = focus; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public byte[] getImageData() { return imageData; }
    public void setImageData(byte[] imageData) { this.imageData = imageData; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

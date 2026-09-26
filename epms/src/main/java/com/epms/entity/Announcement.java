package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "announcements")
@Data
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "announcement_id")
    private Long announcementId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // ALL, or a role name (e.g. AUTHOR) to show the announcement only to that role
    @Column(name = "audience", nullable = false, length = 30)
    private String audience = "ALL";

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** DRAFT (no publish date), SCHEDULED, ACTIVE or EXPIRED, derived from the publish window. */
    @Transient
    public String getStatus() {
        LocalDateTime now = LocalDateTime.now();
        if (publishedAt == null) return "DRAFT";
        if (publishedAt.isAfter(now)) return "SCHEDULED";
        if (expiresAt != null && !expiresAt.isAfter(now)) return "EXPIRED";
        return "ACTIVE";
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

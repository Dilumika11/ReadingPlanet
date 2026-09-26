package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Epic 1 ("Getting Published"): a prospective author's application with a
 * sample manuscript, reviewed by an admin before they register as an author.
 * PENDING -> APPROVED / REJECTED.
 */
@Entity
@Table(name = "publishing_applications")
@Data
public class PublishingApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "author_name", nullable = false, length = 150)
    private String authorName;

    @Column(name = "contact_information", length = 500)
    private String contactInformation;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "manuscript_name", nullable = false)
    private String manuscriptName;

    @Column(name = "book_type", nullable = false, length = 100)
    private String bookType;

    @Column(name = "short_description", columnDefinition = "TEXT")
    private String shortDescription;

    @Column(name = "file_name")
    private String fileName;

    // Relative to the private documents folder
    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_type", length = 150)
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    // The author account registered with this e-mail after approval
    @Column(name = "author_user_id")
    private Long authorUserId;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @PrePersist
    protected void onCreate() {
        if (submittedAt == null) {
            submittedAt = LocalDateTime.now();
        }
    }
}

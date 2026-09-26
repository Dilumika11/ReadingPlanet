package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** Epic 2: the author's formal approval or rejection of a design version. */
@Entity
@Table(name = "author_approvals")
@Data
public class AuthorApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_id")
    private Long approvalId;

    @Column(name = "design_id", nullable = false)
    private Long designId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    // APPROVED or REJECTED
    @Column(name = "approval_status", nullable = false, length = 30)
    private String approvalStatus;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (reviewedAt == null) {
            reviewedAt = createdAt;
        }
    }
}

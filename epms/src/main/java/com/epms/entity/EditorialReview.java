package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** Epic 2: an editor's review of one round, with the decision taken. */
@Entity
@Table(name = "editorial_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uq_editorial_review_round", columnNames = {"manuscript_id", "review_round"}))
@Data
public class EditorialReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    @Column(name = "manuscript_id", nullable = false)
    private Long manuscriptId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "review_round", nullable = false)
    private Integer reviewRound;

    @Column(name = "review_comments", nullable = false, columnDefinition = "TEXT")
    private String reviewComments;

    // ACCEPT, REVISION_REQUIRED or REJECT
    @Column(name = "decision", nullable = false, length = 30)
    private String decision;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (reviewedAt == null) {
            reviewedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

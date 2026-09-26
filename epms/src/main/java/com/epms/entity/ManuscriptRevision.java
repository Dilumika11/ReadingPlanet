package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Epic 1/2: an editor's revision request (round) and the author's response. */
@Entity
@Table(name = "manuscript_revisions",
        uniqueConstraints = @UniqueConstraint(name = "uq_revision_round", columnNames = {"manuscript_id", "revision_round"}))
@Data
public class ManuscriptRevision {

    public static final String PENDING = "PENDING";
    public static final String RESPONDED = "RESPONDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "revision_id")
    private Long revisionId;

    @Column(name = "manuscript_id", nullable = false)
    private Long manuscriptId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "revision_round", nullable = false)
    private Integer revisionRound;

    @Column(name = "editor_comments", nullable = false, columnDefinition = "TEXT")
    private String editorComments;

    @Column(name = "response_deadline")
    private LocalDate responseDeadline;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "response_notes", columnDefinition = "TEXT")
    private String responseNotes;

    @Column(name = "response_file_id")
    private Long responseFileId;

    @Column(name = "status", nullable = false, length = 30)
    private String status = PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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

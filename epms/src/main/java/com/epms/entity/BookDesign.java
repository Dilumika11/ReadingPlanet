package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Epic 2: one version of a book's design. Every upload creates a new
 * version, so earlier covers and layouts are never overwritten.
 * Status: DRAFT -> SUBMITTED (sent to the author) -> APPROVED / REJECTED;
 * SUPERSEDED once a newer version exists.
 */
@Entity
@Table(name = "book_designs",
        uniqueConstraints = @UniqueConstraint(name = "uq_book_design_version", columnNames = {"manuscript_id", "design_version"}))
@Data
public class BookDesign {

    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String SUPERSEDED = "SUPERSEDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "design_id")
    private Long designId;

    @Column(name = "manuscript_id", nullable = false)
    private Long manuscriptId;

    @Column(name = "designer_id", nullable = false)
    private Long designerId;

    @Column(name = "design_version", nullable = false)
    private Integer designVersion;

    @Column(name = "cover_file_path", length = 500)
    private String coverFilePath;

    @Column(name = "layout_file_path", length = 500)
    private String layoutFilePath;

    @Column(name = "print_file_path", length = 500)
    private String printFilePath;

    @Column(name = "design_notes", columnDefinition = "TEXT")
    private String designNotes;

    @Column(name = "design_status", nullable = false, length = 30)
    private String designStatus = DRAFT;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

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

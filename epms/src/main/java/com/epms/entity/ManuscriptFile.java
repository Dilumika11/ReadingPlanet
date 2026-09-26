package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** Epic 1: one uploaded file of a manuscript. file_version counts up per manuscript and is never reused. */
@Entity
@Table(name = "manuscript_files",
        uniqueConstraints = @UniqueConstraint(name = "uq_manuscript_version", columnNames = {"manuscript_id", "file_version"}))
@Data
public class ManuscriptFile {

    public static final String MANUSCRIPT = "MANUSCRIPT";
    public static final String SUPPORTING = "SUPPORTING";
    public static final String REVISION = "REVISION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "manuscript_file_id")
    private Long manuscriptFileId;

    @Column(name = "manuscript_id", nullable = false)
    private Long manuscriptId;

    // Original name as uploaded (shown to users)
    @Column(name = "file_name", nullable = false)
    private String fileName;

    // Stored location, relative to the private documents folder
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_type", nullable = false, length = 50)
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_version", nullable = false)
    private Integer fileVersion;

    // MANUSCRIPT, SUPPORTING or REVISION
    @Column(name = "file_category", nullable = false, length = 20)
    private String fileCategory = MANUSCRIPT;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
    }
}

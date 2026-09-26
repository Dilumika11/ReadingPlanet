package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** Epic 2: a production quality check of an approved design (US19). */
@Entity
@Table(name = "production_quality_checks")
@Data
public class QualityCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "qc_id")
    private Long qcId;

    @Column(name = "manuscript_id", nullable = false)
    private Long manuscriptId;

    @Column(name = "design_id", nullable = false)
    private Long designId;

    @Column(name = "checked_by", nullable = false)
    private Long checkedBy;

    // PASSED or FAILED
    @Column(name = "result", nullable = false, length = 20)
    private String result;

    // Checklist answers as JSON, e.g. {"coverResolution":true,...}
    @Column(name = "checklist", columnDefinition = "TEXT")
    private String checklist;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @PrePersist
    protected void onCreate() {
        if (checkedAt == null) {
            checkedAt = LocalDateTime.now();
        }
    }
}

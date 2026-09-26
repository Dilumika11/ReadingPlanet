package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Epic 2: the designer responsible for an accepted manuscript.
 * ACTIVE -> COMPLETED (author approved the design) or CANCELLED (reassigned).
 * At most one ACTIVE assignment per manuscript (checked in the service).
 */
@Entity
@Table(name = "design_assignments")
@Data
public class DesignAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    private Long assignmentId;

    @Column(name = "manuscript_id", nullable = false)
    private Long manuscriptId;

    @Column(name = "designer_id", nullable = false)
    private Long designerId;

    @Column(name = "assigned_by", nullable = false)
    private Long assignedBy;

    @Column(name = "assignment_status", nullable = false, length = 30)
    private String assignmentStatus = "ACTIVE";

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (assignedAt == null) {
            assignedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "expenses")
@Data
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "expense_id")
    private Long expenseId;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "description")
    private String description;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    // RECORDED -> REVIEWED -> APPROVED -> POSTED (or REJECTED)
    @Column(name = "status", nullable = false, length = 30)
    private String status = "RECORDED";

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    // Stored file name of the uploaded receipt (PDF/JPG/PNG), if any
    @Column(name = "receipt_file")
    private String receiptFile;

    @Column(name = "receipt_content_type", length = 100)
    private String receiptContentType;

    // Set when the expense was posted automatically, e.g. "ROYALTY_PAYMENT:12"
    @Column(name = "source_reference", length = 60)
    private String sourceReference;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

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

package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Epic 4's generic payment record (ROYALTY / EXPENSE / OTHER). Not the
 * same table as Epic 3's customer-order payments ("payments").
 */
@Entity
@Table(name = "financial_payments")
@Data
public class FinancialPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "reference_number", nullable = false, unique = true, length = 100)
    private String referenceNumber;

    // ROYALTY / EXPENSE / OTHER
    @Column(name = "payment_type", nullable = false, length = 30)
    private String paymentType;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";

    // Invoice this payment settles (payment_type INVOICE)
    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "recorded_by")
    private Long recordedBy;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (paymentDate == null) {
            paymentDate = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

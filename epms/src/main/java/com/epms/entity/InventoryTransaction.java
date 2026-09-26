package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Epic 3: every stock movement. Quantity is positive; the type says the
 * direction: RECEIPT, RETURN_IN, ADJUSTMENT_IN add stock; SALE_OUT, DAMAGED,
 * RETURN_OUT, ADJUSTMENT_OUT remove it; RESERVED / RELEASED only move
 * stock between available and reserved.
 */
@Entity
@Table(name = "inventory_transactions")
@Data
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    @Column(name = "performed_by", nullable = false)
    private Long performedBy;

    @Column(name = "transaction_type", nullable = false, length = 30)
    private String transactionType;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (transactionDate == null) {
            transactionDate = createdAt;
        }
    }
}

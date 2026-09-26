package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Epic 4's invoice (general / sale / other), keyed optionally by
 * reference_type + reference_id. Not the same table as Epic 3's
 * customer-order invoices ("invoices").
 *
 * Status: DRAFT -> ISSUED -> PARTIALLY_PAID -> PAID, or CANCELLED.
 * Only DRAFT invoices can be edited. The tax rate and currency are copied
 * from system settings when the invoice is created and never change after.
 */
@Entity
@Table(name = "financial_invoices")
@Data
public class FinancialInvoice {

    public static final String DRAFT = "DRAFT";
    public static final String ISSUED = "ISSUED";
    public static final String PARTIALLY_PAID = "PARTIALLY_PAID";
    public static final String PAID = "PAID";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invoice_id")
    private Long invoiceId;

    // prefix + year + 5-digit sequence, e.g. INV-2026-00042; never reused
    @Column(name = "invoice_number", nullable = false, unique = true, length = 30)
    private String invoiceNumber;

    // GENERAL / SALE / OTHER
    @Column(name = "reference_type", nullable = false, length = 30)
    private String referenceType = "GENERAL";

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "customer_name", length = 150)
    private String customerName;

    @Column(name = "customer_email", length = 150)
    private String customerEmail;

    @Column(name = "customer_address")
    private String customerAddress;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "LKR";

    // Subtotal of the line items
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "status", nullable = false, length = 30)
    private String status = DRAFT;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Total still owed on the invoice. */
    public BigDecimal getOutstanding() {
        return totalAmount.subtract(amountPaid);
    }

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

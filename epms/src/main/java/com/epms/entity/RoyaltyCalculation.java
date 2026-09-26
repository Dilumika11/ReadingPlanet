package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One royalty calculation for an agreement over a sales period, plus the
 * statement / approval / payment workflow that follows it:
 *
 * <pre>
 * CALCULATED -> STATEMENT_ISSUED -> APPROVED -> PAID
 *      |               |      \
 *  CANCELLED     (rejected)    CARRIED_FORWARD (payable below threshold)
 *                -> CALCULATED / CANCELLED
 * </pre>
 *
 * Every sale line that contributed is stored in {@link RoyaltyCalculationLine}
 * so the statement shows exactly how the figure was reached.
 */
@Entity
@Table(
        name = "royalty_calculations",
        uniqueConstraints = {
                // periodLock is 1 while live and NULL once cancelled, so a cancel frees the period.
                @UniqueConstraint(
                        name = "uq_royalty_calc_active_period",
                        columnNames = {"royalty_agreement_id", "sales_period_start", "sales_period_end", "period_lock"}),
                @UniqueConstraint(name = "uq_royalty_statement_number", columnNames = {"statement_number"})
        }
)
@Data
public class RoyaltyCalculation {

    public static final String CALCULATED = "CALCULATED";
    public static final String STATEMENT_ISSUED = "STATEMENT_ISSUED";
    public static final String APPROVED = "APPROVED";
    public static final String CARRIED_FORWARD = "CARRIED_FORWARD";
    public static final String PAID = "PAID";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "calculation_id")
    private Long calculationId;

    @Column(name = "royalty_agreement_id", nullable = false)
    private Long royaltyAgreementId;

    @Column(name = "sales_period_start", nullable = false)
    private LocalDate salesPeriodStart;

    @Column(name = "sales_period_end", nullable = false)
    private LocalDate salesPeriodEnd;

    // Terms copied from the agreement at calculation time
    @Column(name = "basis", length = 20)
    private String basis;

    @Column(name = "royalty_rate", precision = 5, scale = 2)
    private BigDecimal royaltyRate;

    @Column(name = "wholesale_rate", precision = 5, scale = 2)
    private BigDecimal wholesaleRate;

    @Column(name = "books_sold", nullable = false)
    private Integer booksSold = 0;

    @Column(name = "units_returned", nullable = false)
    private Integer unitsReturned = 0;

    @Column(name = "returns_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal returnsAmount = BigDecimal.ZERO;

    // Net sales value of the completed sales in the period
    @Column(name = "gross_sales", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossSales = BigDecimal.ZERO;

    @Column(name = "deductions", nullable = false, precision = 14, scale = 2)
    private BigDecimal deductions = BigDecimal.ZERO;

    // Sum of the line bases (net or list-price value) less deductions
    @Column(name = "royalty_base", nullable = false, precision = 14, scale = 2)
    private BigDecimal royaltyBase = BigDecimal.ZERO;

    // Gross royalty earned in the period (before advance recoupment)
    @Column(name = "royalty_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal royaltyAmount = BigDecimal.ZERO;

    @Column(name = "advance_recouped", nullable = false, precision = 14, scale = 2)
    private BigDecimal advanceRecouped = BigDecimal.ZERO;

    // Payable amounts from earlier CARRIED_FORWARD calculations added to this one
    @Column(name = "carried_forward_in", nullable = false, precision = 14, scale = 2)
    private BigDecimal carriedForwardIn = BigDecimal.ZERO;

    // What the author is owed for this calculation
    @Column(name = "payable_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal payableAmount = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 30)
    private String status = CALCULATED;

    @Column(name = "period_lock")
    private Boolean periodLock = Boolean.TRUE;

    // Set on a CARRIED_FORWARD calculation once a later calculation absorbs it
    @Column(name = "carried_into_calculation_id")
    private Long carriedIntoCalculationId;

    @Column(name = "calculated_by")
    private Long calculatedBy;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @Column(name = "statement_number", length = 30)
    private String statementNumber;

    @Column(name = "statement_issued_by")
    private Long statementIssuedBy;

    @Column(name = "statement_issued_at")
    private LocalDateTime statementIssuedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isCancelled() {
        return CANCELLED.equals(status);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (calculatedAt == null) {
            calculatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

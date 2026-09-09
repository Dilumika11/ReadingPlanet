package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "royalty_calculations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_royalty_calc_period",
                columnNames = {"royalty_agreement_id", "sales_period_start", "sales_period_end"}
        )
)
@Data
public class RoyaltyCalculation {

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

    @Column(name = "books_sold", nullable = false)
    private Integer booksSold = 0;

    @Column(name = "gross_sales", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossSales = BigDecimal.ZERO;

    @Column(name = "deductions", nullable = false, precision = 14, scale = 2)
    private BigDecimal deductions = BigDecimal.ZERO;

    @Column(name = "royalty_base", nullable = false, precision = 14, scale = 2)
    private BigDecimal royaltyBase = BigDecimal.ZERO;

    @Column(name = "royalty_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal royaltyAmount = BigDecimal.ZERO;

    // SALES_RECEIVED -> CALCULATED -> REVIEWED -> APPROVED -> STATEMENT_GENERATED
    @Column(name = "status", nullable = false, length = 30)
    private String status = "CALCULATED";

    @Column(name = "calculated_by")
    private Long calculatedBy;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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

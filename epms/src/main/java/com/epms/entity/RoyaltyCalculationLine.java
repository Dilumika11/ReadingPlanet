package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One sale line behind a royalty calculation. SALE lines earn royalty;
 * RETURN lines record returned units for the statement and earn nothing.
 */
@Entity
@Table(name = "royalty_calculation_lines")
@Data
public class RoyaltyCalculationLine {

    public static final String SALE = "SALE";
    public static final String RETURN = "RETURN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "line_id")
    private Long lineId;

    @Column(name = "calculation_id", nullable = false)
    private Long calculationId;

    @Column(name = "sale_id")
    private Long saleId;

    @Column(name = "sale_reference", length = 40)
    private String saleReference;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "line_type", nullable = false, length = 20)
    private String lineType = SALE;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "base_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal baseAmount;

    @Column(name = "rate_applied", nullable = false, precision = 5, scale = 2)
    private BigDecimal rateApplied;

    @Column(name = "royalty_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal royaltyAmount;
}

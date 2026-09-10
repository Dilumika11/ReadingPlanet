package com.epms.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Interim request shape: until Epic 3 exposes a completed-sales API,
 * the caller supplies the sales figures for the period directly. Swap
 * this for an Epic 3 lookup once that contract exists (see
 * docs/epic-4-spec.pdf section 52).
 */
@Data
public class RoyaltyCalculationRequest {

    @NotNull(message = "Royalty agreement id is required")
    private Long royaltyAgreementId;

    @NotNull(message = "Sales period start is required")
    private LocalDate periodStart;

    @NotNull(message = "Sales period end is required")
    private LocalDate periodEnd;

    @NotNull(message = "Books sold is required")
    @PositiveOrZero(message = "Books sold cannot be negative")
    private Integer booksSold;

    @NotNull(message = "Gross sales is required")
    @PositiveOrZero(message = "Gross sales cannot be negative")
    private BigDecimal grossSales;

    @PositiveOrZero(message = "Deductions cannot be negative")
    private BigDecimal deductions = BigDecimal.ZERO;
}

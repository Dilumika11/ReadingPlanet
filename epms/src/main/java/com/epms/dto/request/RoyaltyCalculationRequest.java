package com.epms.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Sales figures are NOT supplied by the caller: books sold and gross sales
 * are pulled from the COMPLETED sales received from Epic 3 for the
 * agreement's book over the period (docs/epic-4-spec.pdf section 52,
 * business rule "royalties are calculated only from completed sales").
 */
@Data
public class RoyaltyCalculationRequest {

    @NotNull(message = "Royalty agreement id is required")
    private Long royaltyAgreementId;

    @NotNull(message = "Sales period start is required")
    private LocalDate periodStart;

    @NotNull(message = "Sales period end is required")
    private LocalDate periodEnd;

    @PositiveOrZero(message = "Deductions cannot be negative")
    private BigDecimal deductions = BigDecimal.ZERO;
}

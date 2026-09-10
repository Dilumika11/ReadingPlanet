package com.epms.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RoyaltyAgreementRequest {

    @NotNull(message = "Author id is required")
    private Long authorId;

    @NotNull(message = "Book id is required")
    private Long bookId;

    @NotNull(message = "Royalty percentage is required")
    @DecimalMin(value = "0.01", message = "Royalty percentage must be greater than 0")
    @DecimalMax(value = "100.00", message = "Royalty percentage cannot exceed 100")
    private BigDecimal royaltyPercentage;

    @NotNull(message = "Effective date is required")
    private LocalDate effectiveDate;

    private LocalDate expiryDate;
}

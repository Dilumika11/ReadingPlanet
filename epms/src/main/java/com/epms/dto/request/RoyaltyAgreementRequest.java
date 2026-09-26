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
    @DecimalMax(value = "50.00", message = "Royalty percentage cannot exceed 50")
    private BigDecimal royaltyPercentage;

    /** Rate for wholesale (bookstore) sales; empty = same as royaltyPercentage. */
    @DecimalMin(value = "0.00", message = "Wholesale royalty percentage cannot be negative")
    @DecimalMax(value = "50.00", message = "Wholesale royalty percentage cannot exceed 50")
    private BigDecimal wholesaleRoyaltyPercentage;

    /** NET_SALES (default) or LIST_PRICE. */
    @Pattern(regexp = "NET_SALES|LIST_PRICE", message = "Basis must be NET_SALES or LIST_PRICE")
    private String basis;

    @PositiveOrZero(message = "Advance cannot be negative")
    private BigDecimal advanceAmount;

    /** QUARTERLY (default), BIANNUAL or ANNUAL. */
    @Pattern(regexp = "QUARTERLY|BIANNUAL|ANNUAL", message = "Payment frequency must be QUARTERLY, BIANNUAL or ANNUAL")
    private String paymentFrequency;

    @NotNull(message = "Effective date is required")
    private LocalDate effectiveDate;

    private LocalDate expiryDate;
}

package com.epms.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RoyaltyPayRequest {

    @NotBlank(message = "Payment reference is required")
    @Size(max = 100, message = "Payment reference must be 100 characters or fewer")
    private String transactionReference;

    @NotNull(message = "Payment date is required")
    @PastOrPresent(message = "Payment date cannot be in the future")
    private LocalDate paymentDate;

    @NotBlank(message = "Payment method is required")
    @Pattern(regexp = "CASH|BANK_TRANSFER|CARD|CHEQUE", message = "Payment method must be CASH, BANK_TRANSFER, CARD or CHEQUE")
    private String paymentMethod;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;
}

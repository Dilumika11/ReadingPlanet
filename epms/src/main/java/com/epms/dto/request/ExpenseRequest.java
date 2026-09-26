package com.epms.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpenseRequest {

    @NotBlank(message = "Expense category is required")
    @Pattern(regexp = "PRINTING|SALARIES|MARKETING|RENT|UTILITIES|ROYALTY|OTHER",
            message = "Category must be one of PRINTING, SALARIES, MARKETING, RENT, UTILITIES, ROYALTY, OTHER")
    private String category;

    @Size(max = 255, message = "Description must be 255 characters or fewer")
    private String description;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Digits(integer = 10, fraction = 2, message = "Amount can have at most 2 decimal places")
    private BigDecimal amount;

    @NotNull(message = "Expense date is required")
    @PastOrPresent(message = "Expense date cannot be in the future")
    private LocalDate expenseDate;
}

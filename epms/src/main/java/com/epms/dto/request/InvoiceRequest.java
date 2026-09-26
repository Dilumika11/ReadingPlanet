package com.epms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class InvoiceRequest {

    @NotBlank(message = "Customer name is required")
    @Size(max = 150, message = "Customer name must be 150 characters or fewer")
    private String customerName;

    @Email(message = "Customer email is not valid")
    @Size(max = 150)
    private String customerEmail;

    @Size(max = 255, message = "Customer address must be 255 characters or fewer")
    private String customerAddress;

    /** Defaults to today. */
    private LocalDate invoiceDate;

    private LocalDate dueDate;

    @Size(max = 500, message = "Notes must be 500 characters or fewer")
    private String notes;

    @NotEmpty(message = "An invoice needs at least one line item")
    @Valid
    private List<Line> lines;

    @Data
    public static class Line {

        @NotBlank(message = "Each line needs a description")
        @Size(max = 255)
        private String description;

        @NotNull(message = "Each line needs a quantity")
        @Positive(message = "Quantity must be at least 1")
        private Integer quantity;

        @NotNull(message = "Each line needs a unit price")
        @PositiveOrZero(message = "Unit price cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Unit price can have at most 2 decimal places")
        private BigDecimal unitPrice;
    }
}

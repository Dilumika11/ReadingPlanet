package com.epms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BookstoreOrderRequest {

    @NotNull(message = "Choose the bookstore")
    private Long bookstoreId;

    @NotEmpty(message = "Add at least one book")
    @Valid
    private List<Line> items;

    @Data
    public static class Line {
        @NotNull(message = "Each line needs a book")
        private Long bookId;

        @NotNull(message = "Each line needs a quantity")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 10000, message = "Quantity is too large")
        private Integer quantity;

        /** Trade price per copy; empty = 75% of the list price. */
        @DecimalMin(value = "0.01", message = "Unit price must be greater than 0")
        @Digits(integer = 10, fraction = 2)
        private BigDecimal unitPrice;
    }
}

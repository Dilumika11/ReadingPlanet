package com.epms.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ReadyForPrintingRequest {

    @NotBlank(message = "ISBN is required")
    @Pattern(regexp = "^[0-9-]{10,17}$", message = "ISBN must be 10 or 13 digits (hyphens allowed)")
    private String isbn;

    @NotNull(message = "List price is required")
    @DecimalMin(value = "0.01", message = "List price must be greater than 0")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal price;

    @NotNull(message = "Category is required")
    private Long categoryId;

    @Size(max = 50)
    private String edition;

    @Positive(message = "Page count must be positive")
    private Integer totalPages;

    private LocalDate publicationDate;
}

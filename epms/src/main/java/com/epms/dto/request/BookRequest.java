package com.epms.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BookRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255)
    private String title;

    @NotBlank(message = "Author name is required")
    @Size(max = 150)
    private String authorName;

    private Long authorId;

    @NotNull(message = "Category is required")
    private Long categoryId;

    private Long genreId;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", message = "Price cannot be negative")
    private BigDecimal price;

    @Size(max = 20)
    private String isbn;

    @Size(max = 2000)
    private String description;

    private boolean newArrival;
}

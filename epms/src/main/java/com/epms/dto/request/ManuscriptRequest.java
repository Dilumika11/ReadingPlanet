package com.epms.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ManuscriptRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be 255 characters or fewer")
    private String title;

    @NotNull(message = "Genre is required")
    private Long genreId;

    @Size(max = 5000, message = "Synopsis must be 5000 characters or fewer")
    private String synopsis;

    @NotBlank(message = "Language is required")
    @Size(max = 50)
    private String language;

    @Positive(message = "Word count must be positive")
    @Max(value = 2_000_000, message = "Word count looks too large")
    private Integer wordCount;
}

package com.epms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReasonRequest {

    @NotBlank(message = "A reason is required")
    @Size(max = 500, message = "Reason must be 500 characters or fewer")
    private String reason;
}

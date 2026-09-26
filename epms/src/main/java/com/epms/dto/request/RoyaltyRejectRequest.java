package com.epms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoyaltyRejectRequest {

    @NotBlank(message = "A reason is required to reject a statement")
    @Size(max = 500, message = "Reason must be 500 characters or fewer")
    private String reason;

    /** false: back to CALCULATED for correction; true: cancel the calculation (frees the period). */
    private boolean cancel;
}

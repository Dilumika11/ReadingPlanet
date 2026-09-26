package com.epms.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EditorialDecisionRequest {

    /** ACCEPT, REVISION_REQUIRED or REJECT. */
    @NotBlank(message = "Decision is required")
    @Pattern(regexp = "ACCEPT|REVISION_REQUIRED|REJECT", message = "Decision must be ACCEPT, REVISION_REQUIRED or REJECT")
    private String decision;

    @NotBlank(message = "Review comments are required")
    @Size(max = 10000, message = "Comments must be 10000 characters or fewer")
    private String comments;

    /** Required when requesting a revision; must be in the future. */
    @Future(message = "The revision deadline must be in the future")
    private LocalDate revisionDeadline;
}

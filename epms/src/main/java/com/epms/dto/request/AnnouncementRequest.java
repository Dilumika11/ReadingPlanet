package com.epms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AnnouncementRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be 200 characters or fewer")
    private String title;

    @NotBlank(message = "Content is required")
    private String content;

    /** ALL (default) or a role name such as AUTHOR or FINANCE_STAFF. */
    private String audience;

    /** First day it is shown; empty = from now. Ignored when draft is true. */
    private LocalDate publishFrom;

    /** Last day it is shown; empty = no end. */
    private LocalDate publishTo;

    /** Keep as an unpublished draft. */
    private boolean draft;
}

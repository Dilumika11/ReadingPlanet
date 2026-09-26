package com.epms.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class QualityCheckRequest {

    /** Answer for every checklist item (see ProductionService.CHECKLIST); all true = passed. */
    @NotNull(message = "Complete the checklist")
    private Map<String, Boolean> checklist;

    @Size(max = 5000)
    private String notes;
}

package com.epms.dto.response;

import com.epms.entity.Manuscript;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

/** One row in a manuscript list. */
@Data
@AllArgsConstructor
public class ManuscriptSummary {
    private Manuscript manuscript;
    private String authorName;
    private String genreName;
    private String editorName;
    private int reviewRounds;
    private int revisionRounds;
    /** Deadline of an open revision request, if any. */
    private LocalDate revisionDeadline;
    private boolean revisionOverdue;
    private long daysInStatus;
}

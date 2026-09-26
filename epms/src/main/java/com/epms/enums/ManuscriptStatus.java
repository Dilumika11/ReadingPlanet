package com.epms.enums;

import java.util.List;

/**
 * Manuscript workflow across Epics 1 and 2:
 *
 * <pre>
 * DRAFT -> SUBMITTED -> UNDER_REVIEW -> ACCEPTED -> IN_DESIGN -> AWAITING_AUTHOR_APPROVAL
 *                           |   ^   \-> REJECTED                  |            |
 *              REVISION_REQUESTED -> RESUBMITTED            (rejected)   DESIGN_APPROVED
 *                                                                           |
 *                                   PUBLISHED <- READY_FOR_PRINTING <- QC_PASSED (QC failed -> IN_DESIGN)
 * </pre>
 */
public final class ManuscriptStatus {

    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String UNDER_REVIEW = "UNDER_REVIEW";
    public static final String REVISION_REQUESTED = "REVISION_REQUESTED";
    public static final String RESUBMITTED = "RESUBMITTED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";
    public static final String IN_DESIGN = "IN_DESIGN";
    public static final String AWAITING_AUTHOR_APPROVAL = "AWAITING_AUTHOR_APPROVAL";
    public static final String DESIGN_APPROVED = "DESIGN_APPROVED";
    public static final String QC_PASSED = "QC_PASSED";
    public static final String READY_FOR_PRINTING = "READY_FOR_PRINTING";
    public static final String PUBLISHED = "PUBLISHED";

    /** In the editorial part of the workflow (Epic 2, US11 - US15). */
    public static final List<String> EDITORIAL = List.of(SUBMITTED, UNDER_REVIEW, REVISION_REQUESTED, RESUBMITTED);

    /** Accepted and moving through design / production (US16 - US20). */
    public static final List<String> PRODUCTION = List.of(ACCEPTED, IN_DESIGN, AWAITING_AUTHOR_APPROVAL,
            DESIGN_APPROVED, QC_PASSED, READY_FOR_PRINTING, PUBLISHED);

    private ManuscriptStatus() {
    }
}

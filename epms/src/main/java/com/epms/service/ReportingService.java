package com.epms.service;

import com.epms.entity.FinancialReport;

import java.time.LocalDate;
import java.util.List;

/**
 * TODO (Epic 4): financial report generation.
 * Workflow per docs/epic-4-spec.pdf section 9:
 * REQUESTED -> COLLECTING_DATA -> GENERATED -> REVIEWED -> FINALIZED.
 * A FINALIZED report must become read-only (see FinancialReport.isFinalized()).
 */
public interface ReportingService {

    List<FinancialReport> getAll();

    FinancialReport getById(Long id);

    FinancialReport generate(String reportType, LocalDate periodStart, LocalDate periodEnd, Long generatedBy);

    byte[] download(Long id);

    FinancialReport finalizeReport(Long id);
}

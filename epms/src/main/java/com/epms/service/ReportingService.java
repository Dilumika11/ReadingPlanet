package com.epms.service;

import com.epms.dto.response.ReportView;
import com.epms.entity.FinancialReport;

import java.time.LocalDate;
import java.util.List;

/**
 * Financial reports (US39, US40): REVENUE, EXPENSE or PROFIT_AND_LOSS for a
 * period. The figures are stored as a snapshot when generated, so a report
 * re-opens exactly as it was. Workflow GENERATED -> REVIEWED -> FINALIZED;
 * a FINALIZED report is read-only (any change is rejected with 409).
 */
public interface ReportingService {

    List<String> REPORT_TYPES = List.of("REVENUE", "EXPENSE", "PROFIT_AND_LOSS");

    List<FinancialReport> getAll();

    FinancialReport getById(Long id);

    ReportView getView(Long id);

    ReportView generate(String reportType, LocalDate periodStart, LocalDate periodEnd, Long generatedBy);

    /** Re-collects the figures of a report that is not finalized yet. */
    ReportView regenerate(Long id, Long userId);

    ReportView review(Long id, Long userId);

    ReportView finalizeReport(Long id, Long userId);

    void delete(Long id);

    /** CSV export of the stored snapshot. */
    byte[] download(Long id);
}

package com.epms.service.impl;

import com.epms.entity.FinancialReport;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.FinancialReportRepository;
import com.epms.service.ReportingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportingServiceImpl implements ReportingService {

    private final FinancialReportRepository financialReportRepository;

    @Override
    public List<FinancialReport> getAll() {
        return financialReportRepository.findAll();
    }

    @Override
    public FinancialReport getById(Long id) {
        return financialReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Financial report not found: " + id));
    }

    @Override
    public FinancialReport generate(String reportType, LocalDate periodStart, LocalDate periodEnd, Long generatedBy) {
        // TODO: collect sales (Epic 3), royalty (this module) and expense
        // data for [periodStart, periodEnd] and aggregate. See
        // docs/epic-4-spec.pdf section 36 (API Sequence - Financial Report).
        throw new UnsupportedOperationException(
                "Financial report generation not yet implemented — see docs/epic-4-spec.pdf section 36");
    }

    @Override
    public byte[] download(Long id) {
        throw new UnsupportedOperationException(
                "Report file export not yet implemented — see docs/epic-4-spec.pdf section 23");
    }

    @Override
    public FinancialReport finalizeReport(Long id) {

        FinancialReport report = getById(id);

        if (report.isFinalized()) {
            throw new BusinessRuleException("Financial report " + id + " is already finalized and read-only");
        }

        report.setStatus("FINALIZED");
        return financialReportRepository.save(report);
    }
}

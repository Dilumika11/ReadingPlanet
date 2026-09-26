package com.epms.service;

import com.epms.entity.FinancialReport;
import com.epms.exception.BusinessRuleException;
import com.epms.repository.ExpenseRepository;
import com.epms.repository.FinancialReportRepository;
import com.epms.repository.RoyaltyPaymentRepository;
import com.epms.service.impl.ReportingServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US40: a finalized report is read-only. */
@ExtendWith(MockitoExtension.class)
class ReportingServiceImplTest {

    @Mock FinancialReportRepository reportRepository;
    @Mock ExpenseRepository expenseRepository;
    @Mock RoyaltyPaymentRepository royaltyPaymentRepository;
    @Mock SalesDataService salesDataService;
    @Mock SettingsService settingsService;
    @Mock AuditService auditService;

    ReportingServiceImpl service;
    FinancialReport report;

    @BeforeEach
    void setUp() {
        service = new ReportingServiceImpl(reportRepository, expenseRepository, royaltyPaymentRepository,
                salesDataService, settingsService, auditService, new ObjectMapper());
        report = new FinancialReport();
        report.setReportId(1L);
        report.setReportType("REVENUE");
        report.setPeriodStart(LocalDate.of(2026, 1, 1));
        report.setPeriodEnd(LocalDate.of(2026, 6, 30));
        report.setStatus("FINALIZED");
        report.setSnapshotJson("{\"netRevenue\":100}");
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));
    }

    @Test
    void finalizedReportCannotBeChangedDeletedOrFinalizedAgain() {
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("read-only");
        assertThatThrownBy(() -> service.regenerate(1L, 9L)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.review(1L, 9L)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.finalizeReport(1L, 9L)).isInstanceOf(BusinessRuleException.class);
        verify(reportRepository, never()).delete(any());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void finalizingRecordsWhoAndWhen() {
        report.setStatus("REVIEWED");
        when(reportRepository.save(any(FinancialReport.class))).thenAnswer(inv -> inv.getArgument(0));

        var view = service.finalizeReport(1L, 9L);

        assertThat(view.getReport().getStatus()).isEqualTo("FINALIZED");
        assertThat(view.getReport().getFinalizedBy()).isEqualTo(9L);
        assertThat(view.getReport().getFinalizedAt()).isNotNull();
        assertThat(view.getFigures()).containsEntry("netRevenue", 100);
    }

    @Test
    void downloadExportsTheStoredSnapshotAsCsv() {
        String csv = new String(service.download(1L));
        assertThat(csv).contains("Report,REVENUE").contains("netRevenue,100");
    }
}

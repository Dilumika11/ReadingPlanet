package com.epms.service.impl;

import com.epms.dto.response.ReportView;
import com.epms.dto.response.RevenueSummaryResponse;
import com.epms.entity.Expense;
import com.epms.entity.FinancialReport;
import com.epms.entity.RoyaltyPayment;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.InvalidRequestException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.ExpenseRepository;
import com.epms.repository.FinancialReportRepository;
import com.epms.repository.RoyaltyPaymentRepository;
import com.epms.service.AuditService;
import com.epms.service.ReportingService;
import com.epms.service.SalesDataService;
import com.epms.service.SettingsService;
import com.epms.validation.DateRanges;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Transactional
public class ReportingServiceImpl implements ReportingService {

    private static final String GENERATED = "GENERATED";
    private static final String REVIEWED = "REVIEWED";
    private static final String FINALIZED = "FINALIZED";

    private static final List<String> COUNTED_EXPENSE_STATUSES = List.of("APPROVED", "POSTED");

    private final FinancialReportRepository financialReportRepository;
    private final ExpenseRepository expenseRepository;
    private final RoyaltyPaymentRepository royaltyPaymentRepository;
    private final SalesDataService salesDataService;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<FinancialReport> getAll() {
        return financialReportRepository.findAllByOrderByGeneratedDateDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialReport getById(Long id) {
        return financialReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Financial report not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public ReportView getView(Long id) {
        return view(getById(id));
    }

    @Override
    public ReportView generate(String reportType, LocalDate periodStart, LocalDate periodEnd, Long generatedBy) {
        String type = reportType == null ? "" : reportType.trim().toUpperCase();
        if (!REPORT_TYPES.contains(type)) {
            throw new InvalidRequestException("Report type must be one of " + String.join(", ", REPORT_TYPES));
        }
        DateRanges.validateFilter(periodStart, periodEnd);

        FinancialReport report = new FinancialReport();
        report.setReportType(type);
        report.setPeriodStart(periodStart);
        report.setPeriodEnd(periodEnd);
        report.setGeneratedBy(generatedBy);
        report.setGeneratedDate(LocalDateTime.now());
        report.setStatus(GENERATED);
        report.setCurrency(settingsService.currency());
        report.setSnapshotJson(toJson(collect(type, periodStart, periodEnd)));

        FinancialReport saved = financialReportRepository.save(report);
        auditService.record(generatedBy, "REPORT_GENERATED", "FinancialReport", saved.getReportId(),
                type + " " + periodStart + " to " + periodEnd);
        return view(saved);
    }

    @Override
    public ReportView regenerate(Long id, Long userId) {
        FinancialReport report = requireNotFinalized(id, "regenerate");
        report.setSnapshotJson(toJson(collect(report.getReportType(), report.getPeriodStart(), report.getPeriodEnd())));
        report.setGeneratedBy(userId);
        report.setGeneratedDate(LocalDateTime.now());
        report.setStatus(GENERATED);
        report.setReviewedBy(null);
        report.setReviewedAt(null);
        return view(financialReportRepository.save(report));
    }

    @Override
    public ReportView review(Long id, Long userId) {
        FinancialReport report = requireNotFinalized(id, "review");
        if (!GENERATED.equals(report.getStatus())) {
            throw new BusinessRuleException("Financial report " + id + " is already " + report.getStatus());
        }
        report.setStatus(REVIEWED);
        report.setReviewedBy(userId);
        report.setReviewedAt(LocalDateTime.now());
        return view(financialReportRepository.save(report));
    }

    @Override
    public ReportView finalizeReport(Long id, Long userId) {

        FinancialReport report = getById(id);

        if (report.isFinalized()) {
            throw new BusinessRuleException("Financial report " + id + " is already finalized and read-only");
        }

        report.setStatus(FINALIZED);
        report.setFinalizedBy(userId);
        report.setFinalizedAt(LocalDateTime.now());
        FinancialReport saved = financialReportRepository.save(report);
        auditService.record(userId, "REPORT_FINALIZED", "FinancialReport", id,
                report.getReportType() + " " + report.getPeriodStart() + " to " + report.getPeriodEnd());
        return view(saved);
    }

    @Override
    public void delete(Long id) {
        financialReportRepository.delete(requireNotFinalized(id, "delete"));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] download(Long id) {
        FinancialReport report = getById(id);
        Map<String, Object> figures = parse(report.getSnapshotJson());

        StringBuilder csv = new StringBuilder();
        csv.append("Report,").append(report.getReportType()).append('\n');
        csv.append("Period,").append(report.getPeriodStart()).append(" to ").append(report.getPeriodEnd()).append('\n');
        csv.append("Status,").append(report.getStatus()).append('\n');
        csv.append("Currency,").append(report.getCurrency() == null ? "" : report.getCurrency()).append('\n');
        csv.append('\n').append("Item,Value\n");
        flatten("", figures, csv);
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private FinancialReport requireNotFinalized(Long id, String action) {
        FinancialReport report = getById(id);
        if (report.isFinalized()) {
            throw new BusinessRuleException("Cannot " + action + " financial report " + id
                    + ": it is finalized and read-only");
        }
        return report;
    }

    // ---- Figures ----

    private Map<String, Object> collect(String type, LocalDate from, LocalDate to) {
        Map<String, Object> out = new LinkedHashMap<>();
        switch (type) {
            case "REVENUE" -> out.putAll(revenueFigures(from, to));
            case "EXPENSE" -> out.putAll(expenseFigures(from, to));
            default -> {
                RevenueSummaryResponse rev = salesDataService.getRevenue(from, to);
                List<Expense> expenses = countedExpenses(from, to);
                BigDecimal operating = sum(expenses.stream()
                        .filter(e -> !RoyaltyPaymentServiceImpl.EXPENSE_CATEGORY_ROYALTY.equals(e.getCategory()))
                        .map(Expense::getAmount).toList());
                BigDecimal royaltiesPaid = sum(royaltyPaymentRepository
                        .findByPaymentStatusAndPaymentDateBetween("PAID", from, to).stream()
                        .map(RoyaltyPayment::getAmount).toList());
                BigDecimal profit = rev.getTotalRevenue().subtract(operating).subtract(royaltiesPaid);

                out.put("grossRevenue", rev.getGrossRevenue());
                out.put("discounts", rev.getTotalDiscounts());
                out.put("netRevenue", rev.getTotalRevenue());
                out.put("operatingExpenses", operating);
                out.put("royaltiesPaid", royaltiesPaid);
                out.put("profit", money(profit));
                out.put("operatingExpensesByCategory", byCategory(expenses.stream()
                        .filter(e -> !RoyaltyPaymentServiceImpl.EXPENSE_CATEGORY_ROYALTY.equals(e.getCategory())).toList()));
                out.put("note", "Profit = net revenue - approved/posted expenses (excluding ROYALTY) - royalties paid. "
                        + "Paid royalties are also posted as ROYALTY expenses, so they are counted once.");
            }
        }
        return out;
    }

    private Map<String, Object> revenueFigures(LocalDate from, LocalDate to) {
        RevenueSummaryResponse rev = salesDataService.getRevenue(from, to);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("grossRevenue", rev.getGrossRevenue());
        out.put("discounts", rev.getTotalDiscounts());
        out.put("netRevenue", rev.getTotalRevenue());
        out.put("completedSales", rev.getCompletedSales());
        out.put("booksSold", rev.getBooksSold());
        out.put("averageSaleValue", rev.getAverageSaleValue());
        out.put("revenueByChannel", rev.getRevenueByChannel());
        Map<String, Object> monthly = new LinkedHashMap<>();
        rev.getMonthlyRevenue().forEach(m -> monthly.put(m.getMonth(), m.getRevenue()));
        out.put("revenueByMonth", monthly);
        Map<String, Object> top = new LinkedHashMap<>();
        rev.getTopBooks().forEach(b -> top.put(b.getBookTitle(), b.getRevenue()));
        out.put("topBooks", top);
        out.put("cancelledExcluded", rev.getExcluded().getCancelledAmount());
        out.put("returnedExcluded", rev.getExcluded().getReturnedAmount());
        return out;
    }

    private Map<String, Object> expenseFigures(LocalDate from, LocalDate to) {
        List<Expense> expenses = countedExpenses(from, to);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalExpenses", sum(expenses.stream().map(Expense::getAmount).toList()));
        out.put("expenseCount", expenses.size());
        out.put("byCategory", byCategory(expenses));
        Map<String, BigDecimal> monthly = new TreeMap<>();
        expenses.forEach(e -> monthly.merge(e.getExpenseDate().toString().substring(0, 7), e.getAmount(), BigDecimal::add));
        out.put("byMonth", monthly);
        out.put("note", "Only APPROVED and POSTED expenses are counted.");
        return out;
    }

    private List<Expense> countedExpenses(LocalDate from, LocalDate to) {
        return expenseRepository.findByStatusInAndExpenseDateBetween(COUNTED_EXPENSE_STATUSES, from, to);
    }

    private static Map<String, BigDecimal> byCategory(List<Expense> expenses) {
        Map<String, BigDecimal> map = new TreeMap<>();
        expenses.forEach(e -> map.merge(e.getCategory(), e.getAmount(), BigDecimal::add));
        return map;
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return money(values.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    // ---- JSON snapshot ----

    private ReportView view(FinancialReport report) {
        return new ReportView(report, parse(report.getSnapshotJson()));
    }

    private String toJson(Map<String, Object> figures) {
        try {
            return objectMapper.writeValueAsString(figures);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not store report figures", e);
        }
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored report figures are unreadable", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> map, StringBuilder csv) {
        map.forEach((k, v) -> {
            String key = prefix.isEmpty() ? k : prefix + " / " + k;
            if (v instanceof Map<?, ?> nested) {
                flatten(key, (Map<String, Object>) nested, csv);
            } else {
                csv.append(csvCell(key)).append(',').append(csvCell(String.valueOf(v))).append('\n');
            }
        });
    }

    private static String csvCell(String s) {
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}

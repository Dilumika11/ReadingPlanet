package com.epms.service.impl;

import com.epms.dto.response.RevenueSummaryResponse;
import com.epms.dto.response.SalesSummaryResponse;
import com.epms.entity.SalesRecord;
import com.epms.repository.SalesRecordRepository;
import com.epms.service.SalesDataService;
import com.epms.validation.DateRanges;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesDataServiceImpl implements SalesDataService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int TOP_BOOKS = 5;

    private final SalesRecordRepository salesRecordRepository;

    @Override
    public List<SalesRecord> getSales(LocalDate from, LocalDate to, String status) {
        validatePeriod(from, to);
        if (status == null || status.isBlank()) {
            return salesRecordRepository.findBySaleDateBetweenOrderBySaleDateDesc(from, to);
        }
        return salesRecordRepository.findByStatusAndSaleDateBetweenOrderBySaleDateDesc(status.toUpperCase(), from, to);
    }

    @Override
    public SalesSummaryResponse getCompletedSalesForBook(Long bookId, LocalDate from, LocalDate to) {
        validatePeriod(from, to);

        List<SalesRecord> completed = salesRecordRepository
                .findByBookIdAndStatusAndSaleDateBetween(bookId, STATUS_COMPLETED, from, to);

        int booksSold = completed.stream().mapToInt(SalesRecord::getQuantity).sum();

        return new SalesSummaryResponse(bookId, from, to, completed.size(), booksSold, sumAmount(completed));
    }

    @Override
    public RevenueSummaryResponse getRevenue(LocalDate from, LocalDate to) {
        validatePeriod(from, to);

        List<SalesRecord> all = salesRecordRepository.findBySaleDateBetweenOrderBySaleDateDesc(from, to);

        List<SalesRecord> completed = withStatus(all, STATUS_COMPLETED);
        List<SalesRecord> cancelled = withStatus(all, STATUS_CANCELLED);
        List<SalesRecord> returned = withStatus(all, STATUS_RETURNED);

        BigDecimal totalRevenue = sumAmount(completed);
        int booksSold = completed.stream().mapToInt(SalesRecord::getQuantity).sum();
        BigDecimal averageSaleValue = completed.isEmpty()
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : totalRevenue.divide(BigDecimal.valueOf(completed.size()), 2, RoundingMode.HALF_UP);

        // By channel (CUSTOMER / BOOKSTORE), stable key order
        Map<String, BigDecimal> byChannel = new TreeMap<>();
        for (SalesRecord s : completed) {
            byChannel.merge(s.getChannel(), s.getSaleAmount(), BigDecimal::add);
        }

        // Monthly trend, oldest -> newest, including empty months in range
        Map<String, List<SalesRecord>> byMonth = completed.stream()
                .collect(Collectors.groupingBy(s -> MONTH.format(s.getSaleDate())));
        List<RevenueSummaryResponse.MonthlyRevenue> monthly = new ArrayList<>();
        for (LocalDate m = from.withDayOfMonth(1); !m.isAfter(to); m = m.plusMonths(1)) {
            String key = MONTH.format(m);
            List<SalesRecord> rows = byMonth.getOrDefault(key, List.of());
            monthly.add(new RevenueSummaryResponse.MonthlyRevenue(
                    key, sumAmount(rows), rows.size(), rows.stream().mapToInt(SalesRecord::getQuantity).sum()));
        }

        // Top books by revenue
        Map<Long, List<SalesRecord>> byBook = completed.stream()
                .collect(Collectors.groupingBy(SalesRecord::getBookId));
        List<RevenueSummaryResponse.BookRevenue> topBooks = byBook.entrySet().stream()
                .map(e -> new RevenueSummaryResponse.BookRevenue(
                        e.getKey(),
                        e.getValue().get(0).getBookTitle(),
                        e.getValue().stream().mapToInt(SalesRecord::getQuantity).sum(),
                        sumAmount(e.getValue())))
                .sorted(Comparator.comparing(RevenueSummaryResponse.BookRevenue::getRevenue).reversed())
                .limit(TOP_BOOKS)
                .collect(Collectors.toList());

        RevenueSummaryResponse.Excluded excluded = new RevenueSummaryResponse.Excluded(
                cancelled.size(), sumAmount(cancelled), returned.size(), sumAmount(returned));

        return new RevenueSummaryResponse(from, to, totalRevenue, completed.size(), booksSold,
                averageSaleValue, byChannel, monthly, topBooks, excluded);
    }

    private static List<SalesRecord> withStatus(List<SalesRecord> rows, String status) {
        return rows.stream()
                .filter(s -> status.equalsIgnoreCase(s.getStatus()))
                .collect(Collectors.toList());
    }

    private static BigDecimal sumAmount(List<SalesRecord> rows) {
        return rows.stream()
                .map(SalesRecord::getSaleAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static void validatePeriod(LocalDate from, LocalDate to) {
        DateRanges.validateFilter(from, to);
    }
}

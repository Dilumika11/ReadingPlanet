package com.epms.service.impl;

import com.epms.contracts.AuthorDirectory;
import com.epms.contracts.BookCatalog;
import com.epms.contracts.dto.AuthorDto;
import com.epms.contracts.dto.BookDto;
import com.epms.dto.response.RevenueSummaryResponse;
import com.epms.entity.Category;
import com.epms.entity.RoyaltyAgreement;
import com.epms.entity.RoyaltyCalculation;
import com.epms.entity.RoyaltyPayment;
import com.epms.entity.SalesRecord;
import com.epms.repository.CategoryRepository;
import com.epms.repository.RoyaltyAgreementRepository;
import com.epms.repository.RoyaltyCalculationRepository;
import com.epms.repository.RoyaltyPaymentRepository;
import com.epms.service.AnalyticsService;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int TOP = 10;
    private static final Set<String> OUTSTANDING = Set.of(
            RoyaltyCalculation.CALCULATED, RoyaltyCalculation.STATEMENT_ISSUED, RoyaltyCalculation.APPROVED);

    private final SalesDataService salesDataService;
    private final BookCatalog bookCatalog;
    private final AuthorDirectory authorDirectory;
    private final CategoryRepository categoryRepository;
    private final RoyaltyAgreementRepository agreementRepository;
    private final RoyaltyCalculationRepository calculationRepository;
    private final RoyaltyPaymentRepository paymentRepository;

    @Override
    public Map<String, Object> getDashboard(LocalDate from, LocalDate to) {
        LocalDate[] p = period(from, to);
        RevenueSummaryResponse rev = salesDataService.getRevenue(p[0], p[1]);
        LocalDate today = LocalDate.now();

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("revenueInRange", rev.getTotalRevenue());
        kpis.put("revenueThisMonth", salesDataService.getRevenue(today.withDayOfMonth(1), today).getTotalRevenue());
        kpis.put("revenueYearToDate", salesDataService.getRevenue(today.withDayOfYear(1), today).getTotalRevenue());
        kpis.put("orders", rev.getCompletedSales());
        kpis.put("unitsSold", rev.getBooksSold());
        kpis.put("averageOrderValue", rev.getAverageSaleValue());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("periodStart", p[0]);
        out.put("periodEnd", p[1]);
        out.put("kpis", kpis);
        out.put("revenueByMonth", rev.getMonthlyRevenue());
        out.put("revenueByChannel", rev.getRevenueByChannel());
        out.put("topCategories", topCategories(completed(p)));
        return out;
    }

    @Override
    public RevenueSummaryResponse getRevenueAnalytics(LocalDate from, LocalDate to) {
        LocalDate[] p = period(from, to);
        return salesDataService.getRevenue(p[0], p[1]);
    }

    @Override
    public Map<String, Object> getSalesAnalytics(LocalDate from, LocalDate to) {
        LocalDate[] p = period(from, to);
        List<SalesRecord> sales = completed(p);

        Map<String, Map<String, Object>> byChannel = new TreeMap<>();
        for (SalesRecord s : sales) {
            Map<String, Object> row = byChannel.computeIfAbsent(s.getChannel(), k -> newRow());
            add(row, s);
        }
        Map<String, Map<String, Object>> byMonth = new LinkedHashMap<>();
        for (LocalDate m = p[0].withDayOfMonth(1); !m.isAfter(p[1]); m = m.plusMonths(1)) {
            byMonth.put(MONTH.format(m), newRow());
        }
        for (SalesRecord s : sales) {
            add(byMonth.get(MONTH.format(s.getSaleDate())), s);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("periodStart", p[0]);
        out.put("periodEnd", p[1]);
        out.put("byChannel", byChannel);
        out.put("byMonth", byMonth);
        return out;
    }

    @Override
    public Map<String, Object> getBookPerformance(LocalDate from, LocalDate to) {
        LocalDate[] p = period(from, to);
        List<Map<String, Object>> rows = perBook(completed(p));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("periodStart", p[0]);
        out.put("periodEnd", p[1]);
        out.put("topByUnits", rows.stream()
                .sorted(Comparator.comparing((Map<String, Object> r) -> (Integer) r.get("units")).reversed())
                .limit(TOP).collect(Collectors.toList()));
        out.put("topByRevenue", rows.stream()
                .sorted(Comparator.comparing((Map<String, Object> r) -> (BigDecimal) r.get("revenue")).reversed())
                .limit(TOP).collect(Collectors.toList()));
        return out;
    }

    @Override
    public Map<String, Object> getBookTrend(Long bookId, LocalDate from, LocalDate to) {
        LocalDate[] p = period(from, to);
        RevenueSummaryResponse rev = salesDataService.getRevenue(p[0], p[1], null, Set.of(bookId));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bookId", bookId);
        out.put("bookTitle", bookCatalog.findBook(bookId).map(BookDto::title)
                .orElse(rev.getTopBooks().isEmpty() ? "Book #" + bookId : rev.getTopBooks().get(0).getBookTitle()));
        out.put("unitsSold", rev.getBooksSold());
        out.put("revenue", rev.getTotalRevenue());
        out.put("byMonth", rev.getMonthlyRevenue());
        return out;
    }

    @Override
    public Map<String, Object> getAuthorPerformance(LocalDate from, LocalDate to) {
        LocalDate[] p = period(from, to);
        Map<Long, Long> authorByBook = authorByBook();
        Map<Long, String> names = authorDirectory.findAll().stream()
                .collect(Collectors.toMap(AuthorDto::authorId, AuthorDto::fullName, (a, b) -> a));

        Map<Long, Map<String, Object>> byAuthor = new HashMap<>();
        Map<Long, Set<Long>> booksByAuthor = new HashMap<>();
        int unattributedUnits = 0;
        for (SalesRecord s : completed(p)) {
            Long authorId = authorByBook.get(s.getBookId());
            if (authorId == null) {
                unattributedUnits += s.getQuantity();
                continue;
            }
            Map<String, Object> row = byAuthor.computeIfAbsent(authorId, id -> {
                Map<String, Object> r = newRow();
                r.put("authorId", id);
                r.put("authorName", names.getOrDefault(id, "Author #" + id));
                return r;
            });
            add(row, s);
            booksByAuthor.computeIfAbsent(authorId, k -> new java.util.HashSet<>()).add(s.getBookId());
        }
        byAuthor.forEach((id, row) -> row.put("books", booksByAuthor.get(id).size()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("periodStart", p[0]);
        out.put("periodEnd", p[1]);
        out.put("topAuthors", byAuthor.values().stream()
                .sorted(Comparator.comparing((Map<String, Object> r) -> (BigDecimal) r.get("revenue")).reversed())
                .limit(TOP).collect(Collectors.toList()));
        out.put("unattributedUnits", unattributedUnits);
        return out;
    }

    @Override
    public Map<String, Object> getRoyaltyAnalytics(LocalDate from, LocalDate to) {
        LocalDate[] p = period(from, to);

        List<RoyaltyPayment> paid = paymentRepository.findByPaymentStatusAndPaymentDateBetween("PAID", p[0], p[1]);
        BigDecimal paidTotal = money(paid.stream().map(RoyaltyPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));

        List<RoyaltyCalculation> outstanding = calculationRepository.findByStatusIn(OUTSTANDING);
        BigDecimal outstandingTotal = money(outstanding.stream().map(RoyaltyCalculation::getPayableAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        // Royalty earned on sales in the range (live calculations whose period ends inside it)
        BigDecimal earned = money(calculationRepository.findAll().stream()
                .filter(c -> !c.isCancelled())
                .filter(c -> !c.getSalesPeriodEnd().isBefore(p[0]) && !c.getSalesPeriodEnd().isAfter(p[1]))
                .map(RoyaltyCalculation::getRoyaltyAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal revenue = salesDataService.getRevenue(p[0], p[1]).getTotalRevenue();
        BigDecimal pctOfRevenue = revenue.signum() == 0 ? BigDecimal.ZERO
                : earned.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP);

        Map<Long, RoyaltyAgreement> agreements = agreementRepository.findAll().stream()
                .collect(Collectors.toMap(RoyaltyAgreement::getRoyaltyAgreementId, Function.identity()));
        Map<Long, String> names = authorDirectory.findAll().stream()
                .collect(Collectors.toMap(AuthorDto::authorId, AuthorDto::fullName, (a, b) -> a));
        Map<Long, Map<String, Object>> liability = new TreeMap<>();
        for (RoyaltyCalculation c : outstanding) {
            RoyaltyAgreement a = agreements.get(c.getRoyaltyAgreementId());
            if (a == null) continue;
            Map<String, Object> row = liability.computeIfAbsent(a.getAuthorId(), id -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("authorId", id);
                r.put("authorName", names.getOrDefault(id, "Author #" + id));
                r.put("calculated", BigDecimal.ZERO);
                r.put("statementIssued", BigDecimal.ZERO);
                r.put("approved", BigDecimal.ZERO);
                r.put("total", BigDecimal.ZERO);
                return r;
            });
            String bucket = switch (c.getStatus()) {
                case RoyaltyCalculation.CALCULATED -> "calculated";
                case RoyaltyCalculation.STATEMENT_ISSUED -> "statementIssued";
                default -> "approved";
            };
            row.put(bucket, ((BigDecimal) row.get(bucket)).add(c.getPayableAmount()));
            row.put("total", ((BigDecimal) row.get("total")).add(c.getPayableAmount()));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("periodStart", p[0]);
        out.put("periodEnd", p[1]);
        out.put("paid", paidTotal);
        out.put("paymentsCount", paid.size());
        out.put("outstanding", outstandingTotal);
        out.put("royaltyEarned", earned);
        out.put("netRevenue", revenue);
        out.put("royaltyPercentOfRevenue", pctOfRevenue);
        out.put("carriedForward", money(calculationRepository.findByStatusIn(Set.of(RoyaltyCalculation.CARRIED_FORWARD))
                .stream().filter(c -> c.getCarriedIntoCalculationId() == null)
                .map(RoyaltyCalculation::getPayableAmount).reduce(BigDecimal.ZERO, BigDecimal::add)));
        out.put("liabilityByAuthor", liability.values().stream()
                .sorted(Comparator.comparing((Map<String, Object> r) -> (BigDecimal) r.get("total")).reversed())
                .collect(Collectors.toList()));
        return out;
    }

    @Override
    public Object getInventoryStatistics() {
        throw new UnsupportedOperationException(
                "Inventory statistics are owned by Epic 3 (Publishing Operations) and are not available from Epic 4 yet");
    }

    // ---- helpers ----

    private static LocalDate[] period(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.withDayOfMonth(1).minusMonths(11);
        DateRanges.validateFilter(start, end);
        return new LocalDate[] {start, end};
    }

    private List<SalesRecord> completed(LocalDate[] p) {
        return salesDataService.getSales(p[0], p[1], SalesDataService.STATUS_COMPLETED);
    }

    private List<Map<String, Object>> perBook(List<SalesRecord> sales) {
        Map<Long, Map<String, Object>> rows = new LinkedHashMap<>();
        for (SalesRecord s : sales) {
            Map<String, Object> row = rows.computeIfAbsent(s.getBookId(), id -> {
                Map<String, Object> r = newRow();
                r.put("bookId", id);
                r.put("bookTitle", s.getBookTitle());
                return r;
            });
            add(row, s);
        }
        return new ArrayList<>(rows.values());
    }

    private List<Map<String, Object>> topCategories(List<SalesRecord> sales) {
        Map<Long, Long> categoryByBook = bookCatalog.findAll().stream()
                .filter(b -> b.categoryId() != null)
                .collect(Collectors.toMap(BookDto::bookId, BookDto::categoryId, (a, b) -> a));
        Map<Long, String> categoryNames = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getCategoryId, Category::getCategoryName));

        Map<String, Map<String, Object>> rows = new HashMap<>();
        for (SalesRecord s : sales) {
            Long categoryId = categoryByBook.get(s.getBookId());
            String name = categoryId == null ? "Uncategorised" : categoryNames.getOrDefault(categoryId, "Category #" + categoryId);
            Map<String, Object> row = rows.computeIfAbsent(name, n -> {
                Map<String, Object> r = newRow();
                r.put("category", n);
                return r;
            });
            add(row, s);
        }
        return rows.values().stream()
                .sorted(Comparator.comparing((Map<String, Object> r) -> (BigDecimal) r.get("revenue")).reversed())
                .limit(TOP).collect(Collectors.toList());
    }

    /** Book -> author from the catalogue, falling back to royalty agreements for books the catalogue lacks. */
    private Map<Long, Long> authorByBook() {
        Map<Long, Long> map = new HashMap<>();
        agreementRepository.findAll().forEach(a -> map.putIfAbsent(a.getBookId(), a.getAuthorId()));
        bookCatalog.findAll().stream().filter(b -> b.authorId() != null).forEach(b -> map.put(b.bookId(), b.authorId()));
        return map;
    }

    private static Map<String, Object> newRow() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("orders", 0);
        r.put("units", 0);
        r.put("revenue", BigDecimal.ZERO.setScale(2));
        return r;
    }

    private static void add(Map<String, Object> row, SalesRecord s) {
        row.put("orders", (Integer) row.get("orders") + 1);
        row.put("units", (Integer) row.get("units") + s.getQuantity());
        row.put("revenue", money(((BigDecimal) row.get("revenue")).add(s.getNetAmount())));
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}

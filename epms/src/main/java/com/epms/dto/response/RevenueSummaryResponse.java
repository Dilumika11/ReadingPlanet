package com.epms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Revenue monitoring view (US38) built only from COMPLETED sales. */
@Data
@AllArgsConstructor
public class RevenueSummaryResponse {

    private LocalDate periodStart;
    private LocalDate periodEnd;

    private BigDecimal totalRevenue;
    private long completedSales;
    private int booksSold;
    private BigDecimal averageSaleValue;

    /** channel (CUSTOMER / BOOKSTORE) -> revenue */
    private Map<String, BigDecimal> revenueByChannel;

    private List<MonthlyRevenue> monthlyRevenue;
    private List<BookRevenue> topBooks;

    /** Transactions that were received but deliberately excluded from revenue. */
    private Excluded excluded;

    @Data
    @AllArgsConstructor
    public static class MonthlyRevenue {
        private String month; // yyyy-MM
        private BigDecimal revenue;
        private long completedSales;
        private int booksSold;
    }

    @Data
    @AllArgsConstructor
    public static class BookRevenue {
        private Long bookId;
        private String bookTitle;
        private int booksSold;
        private BigDecimal revenue;
    }

    @Data
    @AllArgsConstructor
    public static class Excluded {
        private long cancelledCount;
        private BigDecimal cancelledAmount;
        private long returnedCount;
        private BigDecimal returnedAmount;
    }
}

package com.epms.service;

import com.epms.dto.response.RevenueSummaryResponse;

import java.time.LocalDate;
import java.util.Map;

/**
 * Executive analytics (US48 - US50). Read-only aggregates over Epic 4 data
 * and the Epic 1/2/3 ports. Every method takes a date range; null bounds
 * default to the trailing 12 months.
 */
public interface AnalyticsService {

    /** KPIs (revenue this month, year to date, orders, units), revenue by month, channel split, top categories. */
    Map<String, Object> getDashboard(LocalDate from, LocalDate to);

    RevenueSummaryResponse getRevenueAnalytics(LocalDate from, LocalDate to);

    /** Orders and units by channel and by month. */
    Map<String, Object> getSalesAnalytics(LocalDate from, LocalDate to);

    /** Top authors by revenue and units. */
    Map<String, Object> getAuthorPerformance(LocalDate from, LocalDate to);

    /** Top 10 books by units and by revenue. */
    Map<String, Object> getBookPerformance(LocalDate from, LocalDate to);

    /** Units and revenue per month for one book. */
    Map<String, Object> getBookTrend(Long bookId, LocalDate from, LocalDate to);

    /** Paid vs outstanding royalties, royalty as % of revenue, per-author liability. */
    Map<String, Object> getRoyaltyAnalytics(LocalDate from, LocalDate to);

    /** Owned by Epic 3 (inventory); not available from Epic 4. */
    Object getInventoryStatistics();
}

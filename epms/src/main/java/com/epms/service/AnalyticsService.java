package com.epms.service;

/**
 * TODO (Epic 4): business analytics & executive dashboard.
 * All of these require completed-sales data from Epic 3 and
 * author/book metadata from Epic 1 & 2 — see docs/epic-4-spec.pdf
 * sections 13, 34, 50-52. Use optimized aggregate queries here
 * (non-functional requirement: dashboards must not load full
 * transaction history into memory).
 */
public interface AnalyticsService {

    Object getDashboard();

    Object getRevenueAnalytics();

    Object getSalesAnalytics();

    Object getAuthorPerformance();

    Object getBookPerformance();

    Object getRoyaltyAnalytics();

    Object getInventoryStatistics();
}

package com.epms.service.impl;

import com.epms.service.AnalyticsService;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final String NOT_IMPLEMENTED =
            "Analytics not yet implemented — requires Epic 1/2/3 integration, see docs/epic-4-spec.pdf sections 13, 34";

    @Override
    public Object getDashboard() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Object getRevenueAnalytics() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Object getSalesAnalytics() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Object getAuthorPerformance() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Object getBookPerformance() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Object getRoyaltyAnalytics() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Object getInventoryStatistics() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }
}

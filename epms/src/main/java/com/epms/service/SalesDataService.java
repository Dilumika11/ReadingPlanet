package com.epms.service;

import com.epms.dto.response.RevenueSummaryResponse;
import com.epms.dto.response.SalesSummaryResponse;
import com.epms.entity.SalesRecord;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Epic 4's read-only view of Epic 3's sales data (docs/epic-4-spec.pdf
 * section 52). This is the single integration seam: when Epic 3 exposes its
 * completed-sales feed, replace the implementation — revenue monitoring and
 * royalty calculation only depend on this interface.
 *
 * Business rule: only COMPLETED sales contribute to revenue and royalties.
 * CANCELLED and RETURNED sales are received but excluded.
 */
public interface SalesDataService {

    String STATUS_COMPLETED = "COMPLETED";
    String STATUS_CANCELLED = "CANCELLED";
    String STATUS_RETURNED = "RETURNED";

    /** Raw sales received for the period; status may be null for all statuses. */
    List<SalesRecord> getSales(LocalDate from, LocalDate to, String status);

    /** Completed-sales totals for one book in a period (royalty input). */
    SalesSummaryResponse getCompletedSalesForBook(Long bookId, LocalDate from, LocalDate to);

    /** Completed sale lines for one book, oldest first (royalty calculation input). */
    List<SalesRecord> getCompletedSaleLines(Long bookId, LocalDate from, LocalDate to);

    /** Returned sale lines for one book, oldest first (shown on royalty statements). */
    List<SalesRecord> getReturnedSaleLines(Long bookId, LocalDate from, LocalDate to);

    /** Revenue monitoring aggregate for the period (US38). */
    default RevenueSummaryResponse getRevenue(LocalDate from, LocalDate to) {
        return getRevenue(from, to, null, null);
    }

    /**
     * Revenue for the period, optionally limited to one channel
     * (CUSTOMER / BOOKSTORE) and/or a set of book ids (null = no filter).
     * Net revenue = sale amount - discount.
     */
    RevenueSummaryResponse getRevenue(LocalDate from, LocalDate to, String channel, Collection<Long> bookIds);
}

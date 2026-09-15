package com.epms.validation;

import com.epms.exception.InvalidRequestException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Shared date-range rules for filters and calendars, so the revenue filter,
 * sales summary and royalty period all reject the same bad input the same
 * way (HTTP 400 with a message the UI can show verbatim).
 */
public final class DateRanges {

    /** Longest window the reporting filters accept. */
    public static final int MAX_FILTER_YEARS = 5;

    /** Longest sales period a single royalty calculation may cover. */
    public static final int MAX_ROYALTY_PERIOD_DAYS = 366;

    private DateRanges() {}

    /** Both bounds present and start ≤ end. */
    public static void requireOrdered(LocalDate start, LocalDate end, String startLabel, String endLabel) {
        if (start == null || end == null) {
            throw new InvalidRequestException("Both " + startLabel + " and " + endLabel + " are required (YYYY-MM-DD)");
        }
        if (end.isBefore(start)) {
            throw new InvalidRequestException(endLabel + " " + end + " is before " + startLabel + " " + start);
        }
    }

    /** Reporting filters: ordered, start not in the future, at most MAX_FILTER_YEARS wide. */
    public static void validateFilter(LocalDate from, LocalDate to) {
        requireOrdered(from, to, "period start", "period end");
        LocalDate today = LocalDate.now();
        if (from.isAfter(today)) {
            throw new InvalidRequestException("Period start " + from + " is in the future — there are no sales to report yet");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_FILTER_YEARS * 366L) {
            throw new InvalidRequestException("Period is too long — choose a range of " + MAX_FILTER_YEARS + " years or less");
        }
    }

    /** Royalty sales period: ordered, fully in the past, at most one year. */
    public static void validateRoyaltyPeriod(LocalDate start, LocalDate end) {
        requireOrdered(start, end, "sales period start", "sales period end");
        LocalDate today = LocalDate.now();
        if (end.isAfter(today)) {
            throw new InvalidRequestException("Sales period end " + end + " is in the future — royalties are calculated on completed sales only");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_ROYALTY_PERIOD_DAYS) {
            throw new InvalidRequestException("Sales period cannot exceed one year — split it into shorter periods");
        }
    }

    /** Agreement dates: expiry (if given) must be after the effective date. */
    public static void validateAgreementDates(LocalDate effective, LocalDate expiry) {
        if (effective == null) {
            throw new InvalidRequestException("Effective date is required (YYYY-MM-DD)");
        }
        if (expiry != null && !expiry.isAfter(effective)) {
            throw new InvalidRequestException("Expiry date " + expiry + " must be after the effective date " + effective);
        }
    }
}

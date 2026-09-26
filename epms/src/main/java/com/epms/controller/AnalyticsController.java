package com.epms.controller;

import com.epms.dto.response.ApiResponse;
import com.epms.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Executive analytics (US48 - US50). Every endpoint takes optional from/to (default: last 12 months). */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    public ApiResponse<?> dashboard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new ApiResponse<>(true, "Analytics dashboard retrieved", analyticsService.getDashboard(from, to));
    }

    @GetMapping("/revenue")
    public ApiResponse<?> revenue(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new ApiResponse<>(true, "Revenue analytics retrieved", analyticsService.getRevenueAnalytics(from, to));
    }

    @GetMapping("/sales")
    public ApiResponse<?> sales(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new ApiResponse<>(true, "Sales analytics retrieved", analyticsService.getSalesAnalytics(from, to));
    }

    @GetMapping("/authors")
    public ApiResponse<?> authors(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new ApiResponse<>(true, "Author performance retrieved", analyticsService.getAuthorPerformance(from, to));
    }

    @GetMapping("/books")
    public ApiResponse<?> books(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new ApiResponse<>(true, "Book performance retrieved", analyticsService.getBookPerformance(from, to));
    }

    @GetMapping("/books/{bookId}/trend")
    public ApiResponse<?> bookTrend(@PathVariable Long bookId,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new ApiResponse<>(true, "Book trend retrieved", analyticsService.getBookTrend(bookId, from, to));
    }

    @GetMapping("/royalties")
    public ApiResponse<?> royalties(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new ApiResponse<>(true, "Royalty analytics retrieved", analyticsService.getRoyaltyAnalytics(from, to));
    }

    @GetMapping("/inventory")
    public ApiResponse<?> inventory() {
        return new ApiResponse<>(true, "Inventory statistics retrieved", analyticsService.getInventoryStatistics());
    }
}

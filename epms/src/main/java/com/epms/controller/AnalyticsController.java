package com.epms.controller;

import com.epms.dto.response.ApiResponse;
import com.epms.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    public ApiResponse<?> dashboard() {
        return new ApiResponse<>(true, "Analytics dashboard retrieved", analyticsService.getDashboard());
    }

    @GetMapping("/revenue")
    public ApiResponse<?> revenue() {
        return new ApiResponse<>(true, "Revenue analytics retrieved", analyticsService.getRevenueAnalytics());
    }

    @GetMapping("/sales")
    public ApiResponse<?> sales() {
        return new ApiResponse<>(true, "Sales analytics retrieved", analyticsService.getSalesAnalytics());
    }

    @GetMapping("/authors")
    public ApiResponse<?> authors() {
        return new ApiResponse<>(true, "Author performance retrieved", analyticsService.getAuthorPerformance());
    }

    @GetMapping("/books")
    public ApiResponse<?> books() {
        return new ApiResponse<>(true, "Book performance retrieved", analyticsService.getBookPerformance());
    }

    @GetMapping("/royalties")
    public ApiResponse<?> royalties() {
        return new ApiResponse<>(true, "Royalty analytics retrieved", analyticsService.getRoyaltyAnalytics());
    }

    @GetMapping("/inventory")
    public ApiResponse<?> inventory() {
        return new ApiResponse<>(true, "Inventory statistics retrieved", analyticsService.getInventoryStatistics());
    }
}

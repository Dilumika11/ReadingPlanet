package com.epms.controller;

import com.epms.dto.request.ExpenseRequest;
import com.epms.dto.response.ApiResponse;
import com.epms.security.service.CurrentUserService;
import com.epms.service.FinanceService;
import com.epms.service.SalesDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;
    private final CurrentUserService currentUserService;
    private final SalesDataService salesDataService;

    // --- Revenue monitoring (US38) — built from COMPLETED sales received from Epic 3 ---

    @GetMapping("/api/finance/revenue")
    public ApiResponse<?> getRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new ApiResponse<>(true, "Revenue retrieved", financeService.getRevenue(from, to));
    }

    /** The raw sales feed as received from Epic 3, for finance to inspect what revenue is built from. */
    @GetMapping("/api/finance/sales")
    public ApiResponse<?> getSales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status) {
        LocalDate periodEnd = to != null ? to : LocalDate.now();
        LocalDate periodStart = from != null ? from : periodEnd.withDayOfMonth(1).minusMonths(11);
        return new ApiResponse<>(true, "Sales retrieved", salesDataService.getSales(periodStart, periodEnd, status));
    }

    /** Completed-sales totals for one book in a period — what a royalty calculation will be based on. */
    @GetMapping("/api/finance/sales/summary")
    public ApiResponse<?> getSalesSummary(
            @RequestParam Long bookId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new ApiResponse<>(true, "Sales summary retrieved",
                salesDataService.getCompletedSalesForBook(bookId, from, to));
    }

    @GetMapping("/api/finance/expenses")
    public ApiResponse<?> getExpenses() {
        return new ApiResponse<>(true, "Expenses retrieved", financeService.getExpenses());
    }

    @PostMapping("/api/finance/expenses")
    public ApiResponse<?> createExpense(@Valid @RequestBody ExpenseRequest request, Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Expense recorded", financeService.createExpense(request, userId));
    }

    @PutMapping("/api/finance/expenses/{id}")
    public ApiResponse<?> updateExpense(@PathVariable Long id, @Valid @RequestBody ExpenseRequest request) {
        return new ApiResponse<>(true, "Expense updated", financeService.updateExpense(id, request));
    }

    @PostMapping("/api/finance/expenses/{id}/approve")
    public ApiResponse<?> approveExpense(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Expense approved", financeService.approveExpense(id, userId));
    }

    @GetMapping("/api/invoices")
    public ApiResponse<?> getInvoices() {
        return new ApiResponse<>(true, "Invoices retrieved", financeService.getInvoices());
    }

    @PostMapping("/api/invoices/generate")
    public ApiResponse<?> generateInvoice(@RequestParam String referenceType, @RequestParam Long referenceId) {
        return new ApiResponse<>(true, "Invoice generated",
                financeService.generateInvoice(referenceType, referenceId));
    }

    @GetMapping("/api/invoices/{id}")
    public ApiResponse<?> getInvoice(@PathVariable Long id) {
        return new ApiResponse<>(true, "Invoice retrieved", financeService.getInvoice(id));
    }

    @GetMapping("/api/payments")
    public ApiResponse<?> getPayments() {
        return new ApiResponse<>(true, "Payments retrieved", financeService.getPayments());
    }

    @GetMapping("/api/payments/{id}")
    public ApiResponse<?> getPayment(@PathVariable Long id) {
        return new ApiResponse<>(true, "Payment retrieved", financeService.getPayment(id));
    }
}

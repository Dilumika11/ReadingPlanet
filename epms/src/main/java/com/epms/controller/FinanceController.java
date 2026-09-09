package com.epms.controller;

import com.epms.dto.request.ExpenseRequest;
import com.epms.dto.response.ApiResponse;
import com.epms.security.service.CurrentUserService;
import com.epms.service.FinanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/finance/revenue")
    public ApiResponse<?> getRevenue() {
        return new ApiResponse<>(true, "Revenue retrieved", financeService.getRevenue());
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

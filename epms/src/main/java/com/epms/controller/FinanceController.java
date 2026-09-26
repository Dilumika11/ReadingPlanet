package com.epms.controller;

import com.epms.dto.request.ExpenseRequest;
import com.epms.dto.request.InvoicePaymentRequest;
import com.epms.dto.request.InvoiceRequest;
import com.epms.dto.request.ReasonRequest;
import com.epms.dto.response.ApiResponse;
import com.epms.entity.Expense;
import com.epms.security.service.CurrentUserService;
import com.epms.service.FinanceService;
import com.epms.service.SalesDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;
    private final CurrentUserService currentUserService;
    private final SalesDataService salesDataService;

    // --- Revenue monitoring (US35), built from COMPLETED sales received from Epic 3 ---

    @GetMapping("/api/finance/revenue")
    public ApiResponse<?> getRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Long bookId,
            @RequestParam(required = false) Long categoryId) {
        return new ApiResponse<>(true, "Revenue retrieved", financeService.getRevenue(from, to, channel, bookId, categoryId));
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

    /** Completed-sales totals for one book in a period, what a royalty calculation will be based on. */
    @GetMapping("/api/finance/sales/summary")
    public ApiResponse<?> getSalesSummary(
            @RequestParam Long bookId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new ApiResponse<>(true, "Sales summary retrieved",
                salesDataService.getCompletedSalesForBook(bookId, from, to));
    }

    // --- Expenses (US36) ---

    @GetMapping("/api/finance/expenses")
    public ApiResponse<?> getExpenses(@RequestParam(required = false) String status) {
        return new ApiResponse<>(true, "Expenses retrieved", financeService.getExpenses(status));
    }

    @GetMapping("/api/finance/expenses/categories")
    public ApiResponse<?> getExpenseCategories() {
        return new ApiResponse<>(true, "Expense categories", FinanceService.EXPENSE_CATEGORIES);
    }

    @GetMapping("/api/finance/expenses/{id}")
    public ApiResponse<?> getExpense(@PathVariable Long id) {
        return new ApiResponse<>(true, "Expense retrieved", financeService.getExpense(id));
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

    @DeleteMapping("/api/finance/expenses/{id}")
    public ApiResponse<?> deleteExpense(@PathVariable Long id) {
        financeService.deleteExpense(id);
        return new ApiResponse<>(true, "Expense deleted", null);
    }

    @PostMapping("/api/finance/expenses/{id}/review")
    public ApiResponse<?> reviewExpense(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Expense reviewed", financeService.reviewExpense(id, userId));
    }

    @PostMapping("/api/finance/expenses/{id}/approve")
    public ApiResponse<?> approveExpense(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Expense approved", financeService.approveExpense(id, userId));
    }

    @PostMapping("/api/finance/expenses/{id}/reject")
    public ApiResponse<?> rejectExpense(@PathVariable Long id, @Valid @RequestBody ReasonRequest request,
                                        Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Expense rejected", financeService.rejectExpense(id, request.getReason(), userId));
    }

    @PostMapping("/api/finance/expenses/{id}/post")
    public ApiResponse<?> postExpense(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Expense posted", financeService.postExpense(id, userId));
    }

    @PostMapping(value = "/api/finance/expenses/{id}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> uploadReceipt(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return new ApiResponse<>(true, "Receipt uploaded", financeService.uploadReceipt(id, file));
    }

    @GetMapping("/api/finance/expenses/{id}/receipt")
    public ResponseEntity<Resource> downloadReceipt(@PathVariable Long id) {
        Expense expense = financeService.getExpense(id);
        Path path = financeService.receiptPath(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(expense.getReceiptContentType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM_VALUE : expense.getReceiptContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + expense.getReceiptFile() + "\"")
                .body(new FileSystemResource(path));
    }

    // --- Invoices (US37) ---

    @GetMapping("/api/invoices")
    public ApiResponse<?> getInvoices(@RequestParam(required = false) String status) {
        return new ApiResponse<>(true, "Invoices retrieved", financeService.getInvoices(status));
    }

    @GetMapping("/api/invoices/{id}")
    public ApiResponse<?> getInvoice(@PathVariable Long id) {
        return new ApiResponse<>(true, "Invoice retrieved", financeService.getInvoiceDetail(id));
    }

    @PostMapping("/api/invoices")
    public ApiResponse<?> createInvoice(@Valid @RequestBody InvoiceRequest request, Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Invoice created (DRAFT)", financeService.createInvoice(request, userId));
    }

    @PutMapping("/api/invoices/{id}")
    public ApiResponse<?> updateInvoice(@PathVariable Long id, @Valid @RequestBody InvoiceRequest request) {
        return new ApiResponse<>(true, "Invoice updated", financeService.updateInvoice(id, request));
    }

    @PostMapping("/api/invoices/{id}/issue")
    public ApiResponse<?> issueInvoice(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Invoice issued", financeService.issueInvoice(id, userId));
    }

    @PostMapping("/api/invoices/{id}/cancel")
    public ApiResponse<?> cancelInvoice(@PathVariable Long id, @Valid @RequestBody ReasonRequest request,
                                        Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Invoice cancelled", financeService.cancelInvoice(id, request.getReason(), userId));
    }

    @PostMapping("/api/invoices/generate")
    public ApiResponse<?> generateInvoice(@RequestParam String referenceType, @RequestParam Long referenceId,
                                          Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Invoice generated",
                financeService.generateInvoice(referenceType, referenceId, userId));
    }

    // --- Payments (US38) ---

    @PostMapping("/api/invoices/{id}/payments")
    public ApiResponse<?> recordPayment(@PathVariable Long id, @Valid @RequestBody InvoicePaymentRequest request,
                                        Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Payment recorded", financeService.recordPayment(id, request, userId));
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

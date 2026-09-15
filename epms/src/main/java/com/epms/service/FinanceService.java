package com.epms.service;

import com.epms.dto.request.ExpenseRequest;
import com.epms.dto.response.RevenueSummaryResponse;
import com.epms.entity.Expense;
import com.epms.entity.FinancialInvoice;
import com.epms.entity.FinancialPayment;

import java.time.LocalDate;
import java.util.List;

public interface FinanceService {

    // --- Expenses (RECORDED -> REVIEWED -> APPROVED -> POSTED) ---

    List<Expense> getExpenses();

    Expense createExpense(ExpenseRequest request, Long createdBy);

    Expense updateExpense(Long id, ExpenseRequest request);

    Expense approveExpense(Long id, Long approvedBy);

    // --- Invoices / Payments (pass-through reads; generation is TODO) ---

    List<FinancialInvoice> getInvoices();

    FinancialInvoice getInvoice(Long id);

    List<FinancialPayment> getPayments();

    FinancialPayment getPayment(Long id);

    /**
     * Revenue monitoring (US38): aggregates COMPLETED sales received from
     * Epic 3 for the period — see docs/epic-4-spec.pdf sections 32 and 52.
     * Null bounds default to the trailing 12 months.
     */
    RevenueSummaryResponse getRevenue(LocalDate from, LocalDate to);

    /**
     * TODO (Epic 4): invoice generation tied to a sales/royalty reference.
     * See docs/epic-4-spec.pdf section 21.
     */
    FinancialInvoice generateInvoice(String referenceType, Long referenceId);
}

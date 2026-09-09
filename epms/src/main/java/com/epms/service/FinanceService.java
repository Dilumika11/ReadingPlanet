package com.epms.service;

import com.epms.dto.request.ExpenseRequest;
import com.epms.entity.Expense;
import com.epms.entity.FinancialInvoice;
import com.epms.entity.FinancialPayment;

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
     * TODO (Epic 4): revenue monitoring.
     * Requires consuming Epic 3's completed-sales data — see
     * docs/epic-4-spec.pdf section 32 and 52 (Integration With Epic 3).
     */
    Object getRevenue();

    /**
     * TODO (Epic 4): invoice generation tied to a sales/royalty reference.
     * See docs/epic-4-spec.pdf section 21.
     */
    FinancialInvoice generateInvoice(String referenceType, Long referenceId);
}

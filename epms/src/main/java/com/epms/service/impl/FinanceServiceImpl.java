package com.epms.service.impl;

import com.epms.dto.request.ExpenseRequest;
import com.epms.entity.Expense;
import com.epms.entity.FinancialInvoice;
import com.epms.entity.FinancialPayment;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.ExpenseRepository;
import com.epms.repository.FinancialInvoiceRepository;
import com.epms.repository.FinancialPaymentRepository;
import com.epms.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {

    private final ExpenseRepository expenseRepository;
    private final FinancialInvoiceRepository financialInvoiceRepository;
    private final FinancialPaymentRepository financialPaymentRepository;

    @Override
    public List<Expense> getExpenses() {
        return expenseRepository.findAll();
    }

    @Override
    public Expense createExpense(ExpenseRequest request, Long createdBy) {

        Expense expense = new Expense();
        expense.setCategory(request.getCategory());
        expense.setDescription(request.getDescription());
        expense.setAmount(request.getAmount());
        expense.setExpenseDate(request.getExpenseDate());
        expense.setCreatedBy(createdBy);

        return expenseRepository.save(expense);
    }

    @Override
    public Expense updateExpense(Long id, ExpenseRequest request) {

        Expense expense = getExpenseById(id);

        if (!"RECORDED".equalsIgnoreCase(expense.getStatus())) {
            throw new BusinessRuleException(
                    "Cannot edit expense " + id + " once it has left RECORDED status (current: "
                            + expense.getStatus() + ")");
        }

        expense.setCategory(request.getCategory());
        expense.setDescription(request.getDescription());
        expense.setAmount(request.getAmount());
        expense.setExpenseDate(request.getExpenseDate());

        return expenseRepository.save(expense);
    }

    @Override
    public Expense approveExpense(Long id, Long approvedBy) {

        Expense expense = getExpenseById(id);

        if ("APPROVED".equalsIgnoreCase(expense.getStatus()) || "POSTED".equalsIgnoreCase(expense.getStatus())) {
            throw new BusinessRuleException("Expense " + id + " is already " + expense.getStatus());
        }

        expense.setStatus("APPROVED");
        expense.setApprovedBy(approvedBy);

        return expenseRepository.save(expense);
    }

    private Expense getExpenseById(Long id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + id));
    }

    @Override
    public List<FinancialInvoice> getInvoices() {
        return financialInvoiceRepository.findAll();
    }

    @Override
    public FinancialInvoice getInvoice(Long id) {
        return financialInvoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));
    }

    @Override
    public List<FinancialPayment> getPayments() {
        return financialPaymentRepository.findAll();
    }

    @Override
    public FinancialPayment getPayment(Long id) {
        return financialPaymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
    }

    @Override
    public Object getRevenue() {
        throw new UnsupportedOperationException(
                "Revenue monitoring not yet implemented — requires Epic 3 completed-sales integration, "
                        + "see docs/epic-4-spec.pdf section 52");
    }

    @Override
    public FinancialInvoice generateInvoice(String referenceType, Long referenceId) {
        throw new UnsupportedOperationException(
                "Invoice generation not yet implemented — see docs/epic-4-spec.pdf section 21");
    }
}

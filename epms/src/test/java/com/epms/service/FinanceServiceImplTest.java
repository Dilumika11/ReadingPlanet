package com.epms.service;

import com.epms.contracts.BookCatalog;
import com.epms.dto.request.ExpenseRequest;
import com.epms.dto.request.InvoicePaymentRequest;
import com.epms.dto.request.InvoiceRequest;
import com.epms.entity.Expense;
import com.epms.entity.FinancialInvoice;
import com.epms.exception.BusinessRuleException;
import com.epms.repository.ExpenseRepository;
import com.epms.repository.FinancialInvoiceLineRepository;
import com.epms.repository.FinancialInvoiceRepository;
import com.epms.repository.FinancialPaymentRepository;
import com.epms.repository.SalesRecordRepository;
import com.epms.service.impl.FinanceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US36 - US38: expenses, invoices and payments. */
@ExtendWith(MockitoExtension.class)
class FinanceServiceImplTest {

    @Mock ExpenseRepository expenseRepository;
    @Mock FinancialInvoiceRepository invoiceRepository;
    @Mock FinancialInvoiceLineRepository lineRepository;
    @Mock FinancialPaymentRepository paymentRepository;
    @Mock SalesRecordRepository salesRecordRepository;
    @Mock SalesDataService salesDataService;
    @Mock BookCatalog bookCatalog;
    @Mock SettingsService settingsService;
    @Mock DocumentNumberService documentNumberService;
    @Mock AuditService auditService;

    @InjectMocks FinanceServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(settingsService.invoicePrefix()).thenReturn("INV-");
        lenient().when(settingsService.currency()).thenReturn("LKR");
        lenient().when(settingsService.taxRatePercent()).thenReturn(new BigDecimal("18.00"));
        lenient().when(invoiceRepository.save(any(FinancialInvoice.class))).thenAnswer(inv -> {
            FinancialInvoice i = inv.getArgument(0);
            if (i.getInvoiceId() == null) i.setInvoiceId(1L);
            return i;
        });
        lenient().when(lineRepository.findByInvoiceIdOrderByLineNo(any())).thenReturn(List.of());
        lenient().when(paymentRepository.findByInvoiceIdOrderByPaymentDateAsc(any())).thenReturn(List.of());
    }

    @Test
    void newInvoiceGetsGeneratedNumberAndTaxRateCopiedFromSettings() {
        when(documentNumberService.next("INV-", "INVOICE", LocalDate.now().getYear())).thenReturn("INV-2026-00042");

        FinancialInvoice inv = service.createInvoice(invoiceRequest(), 9L).getInvoice();

        assertThat(inv.getInvoiceNumber()).isEqualTo("INV-2026-00042");
        assertThat(inv.getStatus()).isEqualTo("DRAFT");
        assertThat(inv.getTaxRate()).isEqualByComparingTo("18.00");
        // 2 x 1000 + 500 = 2500; tax 18% = 450
        assertThat(inv.getAmount()).isEqualByComparingTo("2500.00");
        assertThat(inv.getTaxAmount()).isEqualByComparingTo("450.00");
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("2950.00");
    }

    @Test
    void onlyDraftInvoicesCanBeEdited() {
        FinancialInvoice issued = invoice("ISSUED", "1000.00", "0");
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(issued));

        assertThatThrownBy(() -> service.updateInvoice(1L, invoiceRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("only DRAFT");
    }

    @Test
    void paymentCannotExceedTheOutstandingBalance() {
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice("ISSUED", "1000.00", "600.00")));

        assertThatThrownBy(() -> service.recordPayment(1L, payment("400.01", "R-1"), 9L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceeds the outstanding balance 400.00");
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicatePaymentReferenceIsRejected() {
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice("ISSUED", "1000.00", "0")));
        when(paymentRepository.existsByReferenceNumber("R-1")).thenReturn(true);

        assertThatThrownBy(() -> service.recordPayment(1L, payment("100.00", "R-1"), 9L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void invoiceStatusFollowsPayments() {
        FinancialInvoice inv = invoice("ISSUED", "1000.00", "0");
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(inv));

        service.recordPayment(1L, payment("400.00", "R-1"), 9L);
        assertThat(inv.getStatus()).isEqualTo("PARTIALLY_PAID");

        service.recordPayment(1L, payment("600.00", "R-2"), 9L);
        assertThat(inv.getStatus()).isEqualTo("PAID");
        assertThat(inv.getOutstanding()).isEqualByComparingTo("0");
    }

    @Test
    void paymentsOnlyAgainstIssuedInvoices() {
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice("DRAFT", "1000.00", "0")));
        assertThatThrownBy(() -> service.recordPayment(1L, payment("10.00", "R-1"), 9L))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void onlyPendingExpensesCanBeEditedOrDeleted() {
        Expense approved = new Expense();
        approved.setExpenseId(5L);
        approved.setStatus("APPROVED");
        when(expenseRepository.findById(5L)).thenReturn(Optional.of(approved));

        ExpenseRequest r = new ExpenseRequest();
        r.setCategory("RENT");
        r.setAmount(new BigDecimal("10.00"));
        r.setExpenseDate(LocalDate.now());

        assertThatThrownBy(() -> service.updateExpense(5L, r)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.deleteExpense(5L)).isInstanceOf(BusinessRuleException.class);
        verify(expenseRepository, never()).delete(any());
    }

    @Test
    void rejectedExpenseKeepsItsReason() {
        Expense e = new Expense();
        e.setExpenseId(5L);
        e.setStatus("RECORDED");
        when(expenseRepository.findById(5L)).thenReturn(Optional.of(e));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        Expense rejected = service.rejectExpense(5L, "No receipt", 9L);

        assertThat(rejected.getStatus()).isEqualTo("REJECTED");
        assertThat(rejected.getRejectionReason()).isEqualTo("No receipt");
    }

    private static InvoiceRequest invoiceRequest() {
        InvoiceRequest.Line a = new InvoiceRequest.Line();
        a.setDescription("Books");
        a.setQuantity(2);
        a.setUnitPrice(new BigDecimal("1000.00"));
        InvoiceRequest.Line b = new InvoiceRequest.Line();
        b.setDescription("Delivery");
        b.setQuantity(1);
        b.setUnitPrice(new BigDecimal("500.00"));
        InvoiceRequest r = new InvoiceRequest();
        r.setCustomerName("Sarasavi");
        r.setLines(List.of(a, b));
        return r;
    }

    private static FinancialInvoice invoice(String status, String total, String paid) {
        FinancialInvoice i = new FinancialInvoice();
        i.setInvoiceId(1L);
        i.setInvoiceNumber("INV-2026-00001");
        i.setStatus(status);
        i.setTotalAmount(new BigDecimal(total));
        i.setAmountPaid(new BigDecimal(paid));
        return i;
    }

    private static InvoicePaymentRequest payment(String amount, String ref) {
        InvoicePaymentRequest p = new InvoicePaymentRequest();
        p.setAmount(new BigDecimal(amount));
        p.setPaymentDate(LocalDate.now());
        p.setPaymentMethod("CASH");
        p.setReference(ref);
        return p;
    }
}

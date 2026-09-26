package com.epms.service.impl;

import com.epms.contracts.BookCatalog;
import com.epms.contracts.dto.BookDto;
import com.epms.dto.request.ExpenseRequest;
import com.epms.dto.request.InvoicePaymentRequest;
import com.epms.dto.request.InvoiceRequest;
import com.epms.dto.response.InvoiceDetail;
import com.epms.dto.response.RevenueSummaryResponse;
import com.epms.entity.Expense;
import com.epms.entity.FinancialInvoice;
import com.epms.entity.FinancialInvoiceLine;
import com.epms.entity.FinancialPayment;
import com.epms.entity.SalesRecord;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.InvalidRequestException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.ExpenseRepository;
import com.epms.repository.FinancialInvoiceLineRepository;
import com.epms.repository.FinancialInvoiceRepository;
import com.epms.repository.FinancialPaymentRepository;
import com.epms.repository.SalesRecordRepository;
import com.epms.service.AuditService;
import com.epms.service.DocumentNumberService;
import com.epms.service.FinanceService;
import com.epms.service.SalesDataService;
import com.epms.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FinanceServiceImpl implements FinanceService {

    static final String RECORDED = "RECORDED";
    static final String REVIEWED = "REVIEWED";
    static final String APPROVED = "APPROVED";
    static final String POSTED = "POSTED";
    static final String REJECTED = "REJECTED";

    private static final long MAX_RECEIPT_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> RECEIPT_TYPES = Map.of(
            "application/pdf", "pdf", "image/jpeg", "jpg", "image/png", "png");

    private final ExpenseRepository expenseRepository;
    private final FinancialInvoiceRepository financialInvoiceRepository;
    private final FinancialInvoiceLineRepository invoiceLineRepository;
    private final FinancialPaymentRepository financialPaymentRepository;
    private final SalesRecordRepository salesRecordRepository;
    private final SalesDataService salesDataService;
    private final BookCatalog bookCatalog;
    private final SettingsService settingsService;
    private final DocumentNumberService documentNumberService;
    private final AuditService auditService;

    // Receipts are kept outside /uploads (which is publicly served) and only
    // downloaded through the authenticated finance API.
    @Value("${epms.receipts.dir:${user.home}/.readingplanet/receipts}")
    private String receiptsDir;

    // ===================== Expenses =====================

    @Override
    @Transactional(readOnly = true)
    public List<Expense> getExpenses(String status) {
        if (status == null || status.isBlank()) {
            return expenseRepository.findAllByOrderByExpenseDateDescExpenseIdDesc();
        }
        return expenseRepository.findByStatusOrderByExpenseDateDescExpenseIdDesc(status.toUpperCase());
    }

    @Override
    @Transactional(readOnly = true)
    public Expense getExpense(Long id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + id));
    }

    @Override
    public Expense createExpense(ExpenseRequest request, Long createdBy) {

        Expense expense = new Expense();
        applyExpense(expense, request);
        expense.setStatus(RECORDED);
        expense.setCreatedBy(createdBy);

        return expenseRepository.save(expense);
    }

    @Override
    public Expense updateExpense(Long id, ExpenseRequest request) {

        Expense expense = getExpense(id);
        requirePending(expense, "edit");
        applyExpense(expense, request);

        return expenseRepository.save(expense);
    }

    @Override
    public void deleteExpense(Long id) {
        Expense expense = getExpense(id);
        requirePending(expense, "delete");
        deleteReceiptFile(expense);
        expenseRepository.delete(expense);
    }

    @Override
    public Expense reviewExpense(Long id, Long reviewedBy) {
        Expense expense = getExpense(id);
        requireExpenseStatus(expense, Set.of(RECORDED), "review");
        expense.setStatus(REVIEWED);
        expense.setReviewedBy(reviewedBy);
        return expenseRepository.save(expense);
    }

    @Override
    public Expense approveExpense(Long id, Long approvedBy) {

        Expense expense = getExpense(id);
        requireExpenseStatus(expense, Set.of(RECORDED, REVIEWED), "approve");

        expense.setStatus(APPROVED);
        expense.setApprovedBy(approvedBy);
        expense.setRejectionReason(null);

        return expenseRepository.save(expense);
    }

    @Override
    public Expense rejectExpense(Long id, String reason, Long rejectedBy) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException("A reason is required to reject an expense");
        }
        Expense expense = getExpense(id);
        requireExpenseStatus(expense, Set.of(RECORDED, REVIEWED), "reject");
        // Rejected expenses are kept (not deleted) so the decision stays on record.
        expense.setStatus(REJECTED);
        expense.setRejectionReason(reason.trim());
        expense.setReviewedBy(rejectedBy);
        Expense saved = expenseRepository.save(expense);
        auditService.record(rejectedBy, "EXPENSE_REJECTED", "Expense", id, reason.trim());
        return saved;
    }

    @Override
    public Expense postExpense(Long id, Long userId) {
        Expense expense = getExpense(id);
        requireExpenseStatus(expense, Set.of(APPROVED), "post");
        expense.setStatus(POSTED);
        return expenseRepository.save(expense);
    }

    @Override
    public Expense uploadReceipt(Long id, MultipartFile file) {
        Expense expense = getExpense(id);
        requirePending(expense, "attach a receipt to");

        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Choose a receipt file to upload");
        }
        if (file.getSize() > MAX_RECEIPT_BYTES) {
            throw new InvalidRequestException("Receipts must be 5 MB or smaller");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String ext = RECEIPT_TYPES.get(contentType);
        if (ext == null) {
            throw new InvalidRequestException("Receipt must be a PDF, JPG or PNG file (got " + contentType + ")");
        }

        String fileName = "expense-" + id + "-" + System.currentTimeMillis() + "." + ext;
        try {
            Path dir = receiptsRoot();
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessRuleException("Could not store the receipt: " + e.getMessage());
        }
        deleteReceiptFile(expense);
        expense.setReceiptFile(fileName);
        expense.setReceiptContentType(contentType);
        return expenseRepository.save(expense);
    }

    @Override
    @Transactional(readOnly = true)
    public Path receiptPath(Long id) {
        Expense expense = getExpense(id);
        if (expense.getReceiptFile() == null) {
            throw new ResourceNotFoundException("Expense " + id + " has no receipt");
        }
        Path path = receiptsRoot().resolve(expense.getReceiptFile()).normalize();
        if (!path.startsWith(receiptsRoot()) || !Files.exists(path)) {
            throw new ResourceNotFoundException("Receipt file for expense " + id + " is missing");
        }
        return path;
    }

    private static void applyExpense(Expense expense, ExpenseRequest request) {
        expense.setCategory(request.getCategory().toUpperCase());
        expense.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        expense.setAmount(request.getAmount().setScale(2, RoundingMode.HALF_UP));
        expense.setExpenseDate(request.getExpenseDate());
    }

    private static void requirePending(Expense expense, String action) {
        if (!RECORDED.equalsIgnoreCase(expense.getStatus())) {
            throw new BusinessRuleException(
                    "Cannot " + action + " expense " + expense.getExpenseId()
                            + " once it has left RECORDED (pending) status (current: " + expense.getStatus() + ")");
        }
    }

    private static void requireExpenseStatus(Expense expense, Set<String> allowed, String action) {
        if (!allowed.contains(expense.getStatus())) {
            throw new BusinessRuleException("Cannot " + action + " expense " + expense.getExpenseId()
                    + " in status " + expense.getStatus());
        }
    }

    private void deleteReceiptFile(Expense expense) {
        if (expense.getReceiptFile() == null) {
            return;
        }
        try {
            Files.deleteIfExists(receiptsRoot().resolve(expense.getReceiptFile()));
        } catch (IOException e) {
            log.warn("Could not delete receipt {}: {}", expense.getReceiptFile(), e.getMessage());
        }
    }

    private Path receiptsRoot() {
        return Paths.get(receiptsDir).toAbsolutePath().normalize();
    }

    // ===================== Invoices =====================

    @Override
    @Transactional(readOnly = true)
    public List<FinancialInvoice> getInvoices(String status) {
        if (status == null || status.isBlank()) {
            return financialInvoiceRepository.findAllByOrderByInvoiceIdDesc();
        }
        return financialInvoiceRepository.findByStatusOrderByInvoiceIdDesc(status.toUpperCase());
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialInvoice getInvoice(Long id) {
        return financialInvoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceDetail getInvoiceDetail(Long id) {
        return detail(getInvoice(id));
    }

    @Override
    public InvoiceDetail createInvoice(InvoiceRequest request, Long createdBy) {
        LocalDate invoiceDate = request.getInvoiceDate() == null ? LocalDate.now() : request.getInvoiceDate();

        FinancialInvoice invoice = new FinancialInvoice();
        invoice.setInvoiceNumber(documentNumberService.next(
                settingsService.invoicePrefix(), "INVOICE", invoiceDate.getYear()));
        invoice.setReferenceType("GENERAL");
        invoice.setCurrency(settingsService.currency());
        invoice.setTaxRate(settingsService.taxRatePercent());
        invoice.setStatus(FinancialInvoice.DRAFT);
        invoice.setCreatedBy(createdBy);
        applyInvoice(invoice, request, invoiceDate);

        return saveWithLines(invoice, request.getLines());
    }

    @Override
    public InvoiceDetail updateInvoice(Long id, InvoiceRequest request) {
        FinancialInvoice invoice = getInvoice(id);
        requireInvoiceStatus(invoice, FinancialInvoice.DRAFT, "edit");
        applyInvoice(invoice, request, request.getInvoiceDate() == null ? invoice.getInvoiceDate() : request.getInvoiceDate());
        invoiceLineRepository.deleteByInvoiceId(id);
        invoiceLineRepository.flush();
        return saveWithLines(invoice, request.getLines());
    }

    @Override
    public InvoiceDetail issueInvoice(Long id, Long userId) {
        FinancialInvoice invoice = getInvoice(id);
        requireInvoiceStatus(invoice, FinancialInvoice.DRAFT, "issue");
        if (invoice.getTotalAmount().signum() <= 0) {
            throw new BusinessRuleException("Invoice " + invoice.getInvoiceNumber() + " has a zero total and cannot be issued");
        }
        invoice.setStatus(FinancialInvoice.ISSUED);
        invoice.setIssuedAt(LocalDateTime.now());
        FinancialInvoice saved = financialInvoiceRepository.save(invoice);
        auditService.record(userId, "INVOICE_ISSUED", "FinancialInvoice", id, invoice.getInvoiceNumber());
        return detail(saved);
    }

    @Override
    public InvoiceDetail cancelInvoice(Long id, String reason, Long userId) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException("A reason is required to cancel an invoice");
        }
        FinancialInvoice invoice = getInvoice(id);
        if (!FinancialInvoice.DRAFT.equals(invoice.getStatus()) && !FinancialInvoice.ISSUED.equals(invoice.getStatus())) {
            throw new BusinessRuleException("Cannot cancel invoice " + invoice.getInvoiceNumber() + " in status "
                    + invoice.getStatus() + (invoice.getAmountPaid().signum() > 0 ? " (payments have been recorded)" : ""));
        }
        invoice.setStatus(FinancialInvoice.CANCELLED);
        invoice.setCancelReason(reason.trim());
        FinancialInvoice saved = financialInvoiceRepository.save(invoice);
        auditService.record(userId, "INVOICE_CANCELLED", "FinancialInvoice", id,
                invoice.getInvoiceNumber() + ": " + reason.trim());
        return detail(saved);
    }

    @Override
    public InvoiceDetail generateInvoice(String referenceType, Long referenceId, Long createdBy) {
        if (!"SALE".equalsIgnoreCase(referenceType)) {
            throw new InvalidRequestException("Unsupported reference type '" + referenceType
                    + "'. Supported: SALE (create an invoice from a completed sale)");
        }
        SalesRecord sale = salesRecordRepository.findById(referenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found: " + referenceId));
        if (!SalesDataService.STATUS_COMPLETED.equals(sale.getStatus())) {
            throw new BusinessRuleException("Only completed sales can be invoiced; sale " + sale.getSaleReference()
                    + " is " + sale.getStatus());
        }

        InvoiceRequest.Line line = new InvoiceRequest.Line();
        line.setDescription(sale.getBookTitle() + " (" + sale.getSaleReference() + ")");
        line.setQuantity(sale.getQuantity());
        line.setUnitPrice(sale.getUnitPrice());
        List<InvoiceRequest.Line> lines = new ArrayList<>(List.of(line));
        if (sale.getDiscount() != null && sale.getDiscount().signum() > 0) {
            InvoiceRequest.Line discount = new InvoiceRequest.Line();
            discount.setDescription("Discount");
            discount.setQuantity(1);
            discount.setUnitPrice(sale.getDiscount().negate());
            lines.add(discount);
        }

        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerName("BOOKSTORE".equals(sale.getChannel()) ? "Bookstore order " + sale.getSaleReference()
                : "Customer order " + sale.getSaleReference());
        request.setInvoiceDate(LocalDate.now());
        request.setLines(lines);

        InvoiceDetail created = createInvoice(request, createdBy);
        FinancialInvoice invoice = created.getInvoice();
        invoice.setReferenceType("SALE");
        invoice.setReferenceId(sale.getSaleId());
        financialInvoiceRepository.save(invoice);
        return created;
    }

    @Override
    public InvoiceDetail recordPayment(Long invoiceId, InvoicePaymentRequest request, Long userId) {
        FinancialInvoice invoice = getInvoice(invoiceId);
        if (!FinancialInvoice.ISSUED.equals(invoice.getStatus())
                && !FinancialInvoice.PARTIALLY_PAID.equals(invoice.getStatus())) {
            throw new BusinessRuleException("Payments can only be recorded against ISSUED or PARTIALLY_PAID invoices; "
                    + invoice.getInvoiceNumber() + " is " + invoice.getStatus());
        }
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal outstanding = invoice.getOutstanding();
        if (amount.compareTo(outstanding) > 0) {
            throw new BusinessRuleException("Payment " + amount + " exceeds the outstanding balance " + outstanding
                    + " of invoice " + invoice.getInvoiceNumber());
        }
        String reference = request.getReference().trim();
        if (financialPaymentRepository.existsByReferenceNumber(reference)) {
            throw new BusinessRuleException("Payment reference '" + reference + "' has already been used");
        }

        FinancialPayment payment = new FinancialPayment();
        payment.setReferenceNumber(reference);
        payment.setPaymentType("INVOICE");
        payment.setInvoiceId(invoiceId);
        payment.setAmount(amount);
        payment.setPaymentDate(request.getPaymentDate().atStartOfDay());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setStatus("COMPLETED");
        payment.setRecordedBy(userId);
        payment.setDescription("Payment for invoice " + invoice.getInvoiceNumber());
        try {
            financialPaymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("Payment reference '" + reference + "' has already been used");
        }

        invoice.setAmountPaid(invoice.getAmountPaid().add(amount));
        invoice.setStatus(invoice.getOutstanding().signum() == 0 ? FinancialInvoice.PAID : FinancialInvoice.PARTIALLY_PAID);
        FinancialInvoice saved = financialInvoiceRepository.save(invoice);
        auditService.record(userId, "INVOICE_PAYMENT_RECORDED", "FinancialInvoice", invoiceId,
                amount + " by " + request.getPaymentMethod() + ", reference " + reference);
        return detail(saved);
    }

    private static void applyInvoice(FinancialInvoice invoice, InvoiceRequest request, LocalDate invoiceDate) {
        if (request.getDueDate() != null && request.getDueDate().isBefore(invoiceDate)) {
            throw new InvalidRequestException("Due date cannot be before the invoice date");
        }
        invoice.setCustomerName(request.getCustomerName().trim());
        invoice.setCustomerEmail(blankToNull(request.getCustomerEmail()));
        invoice.setCustomerAddress(blankToNull(request.getCustomerAddress()));
        invoice.setInvoiceDate(invoiceDate);
        invoice.setDueDate(request.getDueDate());
        invoice.setNotes(blankToNull(request.getNotes()));
    }

    private InvoiceDetail saveWithLines(FinancialInvoice invoice, List<InvoiceRequest.Line> requestLines) {
        List<FinancialInvoiceLine> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int no = 1;
        for (InvoiceRequest.Line l : requestLines) {
            FinancialInvoiceLine line = new FinancialInvoiceLine();
            line.setLineNo(no++);
            line.setDescription(l.getDescription().trim());
            line.setQuantity(l.getQuantity());
            line.setUnitPrice(l.getUnitPrice().setScale(2, RoundingMode.HALF_UP));
            line.setLineTotal(line.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())).setScale(2, RoundingMode.HALF_UP));
            subtotal = subtotal.add(line.getLineTotal());
            lines.add(line);
        }
        if (subtotal.signum() < 0) {
            throw new InvalidRequestException("The invoice subtotal cannot be negative");
        }
        BigDecimal tax = subtotal.multiply(invoice.getTaxRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        invoice.setAmount(subtotal.setScale(2, RoundingMode.HALF_UP));
        invoice.setTaxAmount(tax);
        invoice.setTotalAmount(invoice.getAmount().add(tax));

        FinancialInvoice saved = financialInvoiceRepository.save(invoice);
        lines.forEach(l -> l.setInvoiceId(saved.getInvoiceId()));
        invoiceLineRepository.saveAll(lines);
        return detail(saved);
    }

    private InvoiceDetail detail(FinancialInvoice invoice) {
        return new InvoiceDetail(invoice,
                invoiceLineRepository.findByInvoiceIdOrderByLineNo(invoice.getInvoiceId()),
                financialPaymentRepository.findByInvoiceIdOrderByPaymentDateAsc(invoice.getInvoiceId()),
                settingsService.companyName(), settingsService.companyAddress());
    }

    private static void requireInvoiceStatus(FinancialInvoice invoice, String status, String action) {
        if (!status.equals(invoice.getStatus())) {
            throw new BusinessRuleException("Cannot " + action + " invoice " + invoice.getInvoiceNumber()
                    + ": only " + status + " invoices can be " + (action.endsWith("e") ? action + "d" : action + "ed")
                    + " (current: " + invoice.getStatus() + ")");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ===================== Payments / revenue =====================

    @Override
    @Transactional(readOnly = true)
    public List<FinancialPayment> getPayments() {
        return financialPaymentRepository.findAllByOrderByPaymentDateDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialPayment getPayment(Long id) {
        return financialPaymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public RevenueSummaryResponse getRevenue(LocalDate from, LocalDate to, String channel, Long bookId, Long categoryId) {
        LocalDate periodEnd = to != null ? to : LocalDate.now();
        LocalDate periodStart = from != null ? from : periodEnd.withDayOfMonth(1).minusMonths(11);

        Set<Long> bookIds = null;
        if (categoryId != null) {
            bookIds = bookCatalog.findAll().stream()
                    .filter(b -> categoryId.equals(b.categoryId()))
                    .map(BookDto::bookId)
                    .collect(Collectors.toSet());
        }
        if (bookId != null) {
            bookIds = bookIds == null ? Set.of(bookId) : (bookIds.contains(bookId) ? Set.of(bookId) : Set.of());
        }
        return salesDataService.getRevenue(periodStart, periodEnd, channel, bookIds);
    }
}

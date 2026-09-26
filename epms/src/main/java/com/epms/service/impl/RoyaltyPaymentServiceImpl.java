package com.epms.service.impl;

import com.epms.dto.request.RoyaltyPayRequest;
import com.epms.entity.Expense;
import com.epms.entity.RoyaltyCalculation;
import com.epms.entity.RoyaltyPayment;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.ExpenseRepository;
import com.epms.repository.RoyaltyCalculationRepository;
import com.epms.repository.RoyaltyPaymentRepository;
import com.epms.service.AuditService;
import com.epms.service.DocumentNumberService;
import com.epms.service.RoyaltyPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class RoyaltyPaymentServiceImpl implements RoyaltyPaymentService {

    public static final String EXPENSE_CATEGORY_ROYALTY = "ROYALTY";

    private static final Set<String> PAYABLE_STATUSES = Set.of("APPROVED", "SCHEDULED", "PROCESSING");

    private final RoyaltyPaymentRepository royaltyPaymentRepository;
    private final RoyaltyCalculationRepository royaltyCalculationRepository;
    private final ExpenseRepository expenseRepository;
    private final DocumentNumberService documentNumberService;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<RoyaltyPayment> getAll() {
        return royaltyPaymentRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public RoyaltyPayment getById(Long id) {
        return royaltyPaymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Royalty payment not found: " + id));
    }

    @Override
    public RoyaltyPayment createForApprovedCalculation(RoyaltyCalculation calculation, Long approvedByUserId) {
        if (royaltyPaymentRepository.findByCalculationId(calculation.getCalculationId()).isPresent()) {
            throw new BusinessRuleException("A payment already exists for royalty calculation "
                    + calculation.getCalculationId());
        }
        RoyaltyPayment payment = new RoyaltyPayment();
        payment.setRoyaltyAgreementId(calculation.getRoyaltyAgreementId());
        payment.setCalculationId(calculation.getCalculationId());
        payment.setPaymentReference(documentNumberService.next("RP-", "ROYALTY_PAYMENT", LocalDate.now().getYear()));
        payment.setAmount(calculation.getPayableAmount());
        payment.setPaymentDate(LocalDate.now());
        payment.setPaymentStatus("APPROVED");
        payment.setApprovedBy(approvedByUserId);
        payment.setApprovedAt(LocalDateTime.now());
        return royaltyPaymentRepository.save(payment);
    }

    @Override
    public RoyaltyPayment approve(Long id, Long approvedByUserId) {

        RoyaltyPayment payment = requireStatus(id, "PENDING", "approve");

        payment.setPaymentStatus("APPROVED");
        payment.setApprovedBy(approvedByUserId);
        payment.setApprovedAt(LocalDateTime.now());

        return royaltyPaymentRepository.save(payment);
    }

    @Override
    public RoyaltyPayment schedule(Long id) {

        RoyaltyPayment payment = requireStatus(id, "APPROVED", "schedule");
        payment.setPaymentStatus("SCHEDULED");

        return royaltyPaymentRepository.save(payment);
    }

    @Override
    public RoyaltyPayment process(Long id) {

        RoyaltyPayment payment = requireStatus(id, "SCHEDULED", "process");
        payment.setPaymentStatus("PROCESSING");

        return royaltyPaymentRepository.save(payment);
    }

    @Override
    public RoyaltyPayment markPaid(Long id, RoyaltyPayRequest request, Long userId) {

        RoyaltyPayment payment = getById(id);

        // Rule: a payment cannot be marked paid without having gone through
        // approval first, and a PAID payment is never changed again.
        if ("PAID".equals(payment.getPaymentStatus())) {
            throw new BusinessRuleException("Royalty payment " + id + " is already PAID and cannot be changed");
        }
        if (!PAYABLE_STATUSES.contains(payment.getPaymentStatus())) {
            throw new BusinessRuleException("Cannot mark payment " + id + " as paid: it must be approved first (status "
                    + payment.getPaymentStatus() + ")");
        }
        if (payment.getAmount().compareTo(request.getAmount()) != 0) {
            throw new BusinessRuleException("Amount " + request.getAmount() + " does not match the approved payable amount "
                    + payment.getAmount());
        }
        String reference = request.getTransactionReference().trim();
        if (royaltyPaymentRepository.existsByTransactionReference(reference)) {
            throw new BusinessRuleException("Payment reference '" + reference
                    + "' has already been used for another royalty payment");
        }

        payment.setTransactionReference(reference);
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setPaymentDate(request.getPaymentDate());
        payment.setPaymentStatus("PAID");
        payment.setPaidAt(LocalDateTime.now());
        payment.setProcessedBy(userId);

        RoyaltyPayment saved;
        try {
            saved = royaltyPaymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            // Backstop: unique index on transaction_reference (concurrent use of the same reference)
            throw new BusinessRuleException("Payment reference '" + reference
                    + "' has already been used for another royalty payment");
        }

        String statement = null;
        if (payment.getCalculationId() != null) {
            RoyaltyCalculation calc = royaltyCalculationRepository.findById(payment.getCalculationId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Royalty calculation not found: " + payment.getCalculationId()));
            calc.setStatus(RoyaltyCalculation.PAID);
            royaltyCalculationRepository.save(calc);
            statement = calc.getStatementNumber();
        }

        // Post the payout as a ROYALTY expense so profit and loss includes it.
        Expense expense = new Expense();
        expense.setCategory(EXPENSE_CATEGORY_ROYALTY);
        expense.setDescription("Royalty payment " + saved.getPaymentReference()
                + (statement == null ? "" : " for statement " + statement) + " (ref " + reference + ")");
        expense.setAmount(saved.getAmount());
        expense.setExpenseDate(saved.getPaymentDate());
        expense.setStatus("POSTED");
        expense.setCreatedBy(userId);
        expense.setApprovedBy(saved.getApprovedBy());
        expense.setSourceReference("ROYALTY_PAYMENT:" + saved.getRoyaltyPaymentId());
        expenseRepository.save(expense);

        auditService.record(userId, "ROYALTY_PAID", "RoyaltyPayment", saved.getRoyaltyPaymentId(),
                "Paid " + saved.getAmount() + " by " + saved.getPaymentMethod() + ", reference " + reference);
        return saved;
    }

    private RoyaltyPayment requireStatus(Long id, String requiredStatus, String action) {

        RoyaltyPayment payment = getById(id);

        if (!requiredStatus.equalsIgnoreCase(payment.getPaymentStatus())) {
            throw new BusinessRuleException(
                    "Cannot " + action + " payment " + id + ": expected status " + requiredStatus
                            + " but was " + payment.getPaymentStatus());
        }

        return payment;
    }
}

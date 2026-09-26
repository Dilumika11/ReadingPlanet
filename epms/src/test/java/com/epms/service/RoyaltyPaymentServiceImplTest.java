package com.epms.service;

import com.epms.dto.request.RoyaltyPayRequest;
import com.epms.entity.Expense;
import com.epms.entity.RoyaltyCalculation;
import com.epms.entity.RoyaltyPayment;
import com.epms.exception.BusinessRuleException;
import com.epms.repository.ExpenseRepository;
import com.epms.repository.RoyaltyCalculationRepository;
import com.epms.repository.RoyaltyPaymentRepository;
import com.epms.service.impl.RoyaltyPaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US46: paying an approved royalty. */
@ExtendWith(MockitoExtension.class)
class RoyaltyPaymentServiceImplTest {

    @Mock RoyaltyPaymentRepository paymentRepository;
    @Mock RoyaltyCalculationRepository calculationRepository;
    @Mock ExpenseRepository expenseRepository;
    @Mock DocumentNumberService documentNumberService;
    @Mock AuditService auditService;

    @InjectMocks RoyaltyPaymentServiceImpl service;

    private RoyaltyPayment payment;
    private RoyaltyCalculation calc;
    private RoyaltyPayRequest request;

    @BeforeEach
    void setUp() {
        payment = new RoyaltyPayment();
        payment.setRoyaltyPaymentId(3L);
        payment.setCalculationId(11L);
        payment.setRoyaltyAgreementId(7L);
        payment.setPaymentReference("RP-2026-00001");
        payment.setAmount(new BigDecimal("5000.00"));
        payment.setPaymentStatus("APPROVED");
        payment.setPaymentDate(LocalDate.now());

        calc = new RoyaltyCalculation();
        calc.setCalculationId(11L);
        calc.setStatus("APPROVED");
        calc.setStatementNumber("RS-2026-00001");

        request = new RoyaltyPayRequest();
        request.setAmount(new BigDecimal("5000.00"));
        request.setPaymentDate(LocalDate.now());
        request.setPaymentMethod("BANK_TRANSFER");
        request.setTransactionReference("BT-777");

        lenient().when(paymentRepository.findById(3L)).thenReturn(Optional.of(payment));
        lenient().when(calculationRepository.findById(11L)).thenReturn(Optional.of(calc));
    }

    @Test
    void markingPaidUpdatesCalculationAndPostsARoyaltyExpense() {
        when(paymentRepository.saveAndFlush(any(RoyaltyPayment.class))).thenAnswer(inv -> inv.getArgument(0));

        RoyaltyPayment paid = service.markPaid(3L, request, 42L);

        assertThat(paid.getPaymentStatus()).isEqualTo("PAID");
        assertThat(paid.getTransactionReference()).isEqualTo("BT-777");
        assertThat(calc.getStatus()).isEqualTo("PAID");
        ArgumentCaptor<Expense> expense = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(expense.capture());
        assertThat(expense.getValue().getCategory()).isEqualTo("ROYALTY");
        assertThat(expense.getValue().getStatus()).isEqualTo("POSTED");
        assertThat(expense.getValue().getAmount()).isEqualByComparingTo("5000.00");
    }

    @Test
    void rejectsADuplicatePaymentReference() {
        when(paymentRepository.existsByTransactionReference("BT-777")).thenReturn(true);

        assertThatThrownBy(() -> service.markPaid(3L, request, 42L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been used");
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void amountMustEqualThePayableAmount() {
        request.setAmount(new BigDecimal("4999.99"));
        assertThatThrownBy(() -> service.markPaid(3L, request, 42L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void cannotPayWithoutApprovalOrPayTwice() {
        payment.setPaymentStatus("PENDING");
        assertThatThrownBy(() -> service.markPaid(3L, request, 42L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("approved first");

        payment.setPaymentStatus("PAID");
        assertThatThrownBy(() -> service.markPaid(3L, request, 42L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already PAID");
    }
}

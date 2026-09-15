package com.epms.service;

import com.epms.dto.request.RoyaltyAgreementRequest;
import com.epms.dto.request.RoyaltyCalculationRequest;
import com.epms.dto.response.SalesSummaryResponse;
import com.epms.entity.RoyaltyAgreement;
import com.epms.entity.RoyaltyCalculation;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.InvalidRequestException;
import com.epms.repository.RoyaltyAgreementRepository;
import com.epms.repository.RoyaltyCalculationRepository;
import com.epms.service.impl.RoyaltyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * US45 — royalties are calculated from COMPLETED sales received from Epic 3,
 * applying the ACTIVE agreement's rate. Covers the spec section 55 checklist
 * items that don't need a database.
 */
@ExtendWith(MockitoExtension.class)
class RoyaltyServiceImplTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2026, 7, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 7, 31);

    @Mock RoyaltyCalculationRepository calculationRepository;
    @Mock RoyaltyAgreementRepository agreementRepository;
    @Mock SalesDataService salesDataService;

    @InjectMocks RoyaltyServiceImpl service;

    private RoyaltyAgreement agreement;
    private RoyaltyCalculationRequest request;

    @BeforeEach
    void setUp() {
        agreement = new RoyaltyAgreement();
        agreement.setRoyaltyAgreementId(7L);
        agreement.setAuthorId(101L);
        agreement.setBookId(1L);
        agreement.setRoyaltyPercentage(new BigDecimal("10.00"));
        agreement.setEffectiveDate(LocalDate.of(2026, 1, 1));
        agreement.setStatus("ACTIVE");

        request = new RoyaltyCalculationRequest();
        request.setRoyaltyAgreementId(7L);
        request.setPeriodStart(PERIOD_START);
        request.setPeriodEnd(PERIOD_END);
        request.setDeductions(BigDecimal.ZERO);

        // lenient: the agreement-creation test never looks the agreement up
        lenient().when(agreementRepository.findById(7L)).thenReturn(Optional.of(agreement));
    }

    @Test
    void calculatesRoyaltyFromCompletedSalesUsingAgreementRate() {
        when(calculationRepository.existsByRoyaltyAgreementIdAndSalesPeriodStartAndSalesPeriodEnd(7L, PERIOD_START, PERIOD_END))
                .thenReturn(false);
        when(salesDataService.getCompletedSalesForBook(1L, PERIOD_START, PERIOD_END))
                .thenReturn(new SalesSummaryResponse(1L, PERIOD_START, PERIOD_END, 12, 54, new BigDecimal("99900.00")));
        when(calculationRepository.saveAndFlush(any(RoyaltyCalculation.class))).thenAnswer(inv -> inv.getArgument(0));

        RoyaltyCalculation result = service.calculate(request, 42L);

        assertThat(result.getBooksSold()).isEqualTo(54);
        assertThat(result.getGrossSales()).isEqualByComparingTo("99900.00");
        assertThat(result.getRoyaltyBase()).isEqualByComparingTo("99900.00");
        assertThat(result.getRoyaltyAmount()).isEqualByComparingTo("9990.00");
        assertThat(result.getStatus()).isEqualTo("CALCULATED");
        assertThat(result.getCalculatedBy()).isEqualTo(42L);
    }

    @Test
    void deductionsReduceTheRoyaltyBase() {
        request.setDeductions(new BigDecimal("1500.00"));
        when(calculationRepository.existsByRoyaltyAgreementIdAndSalesPeriodStartAndSalesPeriodEnd(7L, PERIOD_START, PERIOD_END))
                .thenReturn(false);
        when(salesDataService.getCompletedSalesForBook(1L, PERIOD_START, PERIOD_END))
                .thenReturn(new SalesSummaryResponse(1L, PERIOD_START, PERIOD_END, 3, 10, new BigDecimal("10000.00")));
        when(calculationRepository.saveAndFlush(any(RoyaltyCalculation.class))).thenAnswer(inv -> inv.getArgument(0));

        RoyaltyCalculation result = service.calculate(request, 42L);

        assertThat(result.getRoyaltyBase()).isEqualByComparingTo("8500.00");
        assertThat(result.getRoyaltyAmount()).isEqualByComparingTo("850.00");
    }

    @Test
    void rejectsCalculationWhenAgreementIsNotActive() {
        agreement.setStatus("DRAFT");

        assertThatThrownBy(() -> service.calculate(request, 42L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No active royalty agreement");

        verify(salesDataService, never()).getCompletedSalesForBook(any(), any(), any());
        verify(calculationRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDuplicateCalculationForSameAgreementAndPeriod() {
        when(calculationRepository.existsByRoyaltyAgreementIdAndSalesPeriodStartAndSalesPeriodEnd(7L, PERIOD_START, PERIOD_END))
                .thenReturn(true);

        assertThatThrownBy(() -> service.calculate(request, 42L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");

        verify(calculationRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsPeriodEndingInTheFutureOrLongerThanAYear() {
        request.setPeriodStart(LocalDate.now().minusDays(10));
        request.setPeriodEnd(LocalDate.now().plusDays(1));
        assertThatThrownBy(() -> service.calculate(request, 42L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("in the future");

        request.setPeriodStart(LocalDate.of(2025, 1, 1));
        request.setPeriodEnd(LocalDate.of(2026, 3, 1));
        assertThatThrownBy(() -> service.calculate(request, 42L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("cannot exceed one year");

        request.setPeriodStart(PERIOD_END);
        request.setPeriodEnd(PERIOD_START);
        assertThatThrownBy(() -> service.calculate(request, 42L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("before");

        verify(calculationRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAgreementWhoseExpiryIsNotAfterEffectiveDate() {
        RoyaltyAgreementRequest r = new RoyaltyAgreementRequest();
        r.setAuthorId(101L);
        r.setBookId(1L);
        r.setRoyaltyPercentage(new BigDecimal("10.00"));
        r.setEffectiveDate(LocalDate.of(2026, 1, 1));
        r.setExpiryDate(LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> service.createAgreement(r))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("must be after");
        verify(agreementRepository, never()).save(any());
    }

    @Test
    void rejectsPeriodOutsideAgreementEffectiveRange() {
        request.setPeriodStart(LocalDate.of(2025, 12, 1));
        request.setPeriodEnd(LocalDate.of(2025, 12, 31));

        assertThatThrownBy(() -> service.calculate(request, 42L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("outside the agreement's effective range");
    }

    @Test
    void rejectsPeriodWithNoCompletedSales() {
        when(calculationRepository.existsByRoyaltyAgreementIdAndSalesPeriodStartAndSalesPeriodEnd(7L, PERIOD_START, PERIOD_END))
                .thenReturn(false);
        when(salesDataService.getCompletedSalesForBook(eq(1L), any(), any()))
                .thenReturn(new SalesSummaryResponse(1L, PERIOD_START, PERIOD_END, 0, 0, BigDecimal.ZERO));

        assertThatThrownBy(() -> service.calculate(request, 42L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No completed sales");

        verify(calculationRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDeductionsGreaterThanGrossSales() {
        request.setDeductions(new BigDecimal("20000.00"));
        when(calculationRepository.existsByRoyaltyAgreementIdAndSalesPeriodStartAndSalesPeriodEnd(7L, PERIOD_START, PERIOD_END))
                .thenReturn(false);
        when(salesDataService.getCompletedSalesForBook(1L, PERIOD_START, PERIOD_END))
                .thenReturn(new SalesSummaryResponse(1L, PERIOD_START, PERIOD_END, 3, 10, new BigDecimal("10000.00")));

        assertThatThrownBy(() -> service.calculate(request, 42L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceed gross sales");
    }
}

package com.epms.service;

import com.epms.contracts.AuthorDirectory;
import com.epms.contracts.BookCatalog;
import com.epms.contracts.dto.AuthorDto;
import com.epms.contracts.dto.BookDto;
import com.epms.dto.request.RoyaltyAgreementRequest;
import com.epms.dto.request.RoyaltyCalculationRequest;
import com.epms.entity.RoyaltyAgreement;
import com.epms.entity.RoyaltyCalculation;
import com.epms.entity.SalesRecord;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.InvalidRequestException;
import com.epms.repository.RoyaltyAgreementRepository;
import com.epms.repository.RoyaltyCalculationLineRepository;
import com.epms.repository.RoyaltyCalculationRepository;
import com.epms.repository.RoyaltyPaymentRepository;
import com.epms.service.impl.RoyaltyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * US41 - US47 service rules: agreement validation, calculation guards
 * (active agreement, period, overlap), advance tracking, statement /
 * approval workflow and author ownership.
 */
@ExtendWith(MockitoExtension.class)
class RoyaltyServiceImplTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2026, 7, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 7, 31);

    @Mock RoyaltyCalculationRepository calculationRepository;
    @Mock RoyaltyAgreementRepository agreementRepository;
    @Mock RoyaltyCalculationLineRepository lineRepository;
    @Mock RoyaltyPaymentRepository paymentRepository;
    @Mock SalesDataService salesDataService;
    @Mock AuthorDirectory authorDirectory;
    @Mock BookCatalog bookCatalog;
    @Mock SettingsService settingsService;
    @Mock DocumentNumberService documentNumberService;
    @Mock RoyaltyPaymentService royaltyPaymentService;
    @Mock AuditService auditService;

    @InjectMocks RoyaltyServiceImpl service;

    private RoyaltyAgreement agreement;
    private RoyaltyCalculationRequest request;

    @BeforeEach
    void setUp() {
        agreement = new RoyaltyAgreement();
        agreement.setRoyaltyAgreementId(7L);
        agreement.setAgreementNumber("RA-7");
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

        lenient().when(agreementRepository.findById(7L)).thenReturn(Optional.of(agreement));
    }

    // ---------- calculation ----------

    @Test
    void calculatesFromCompletedSaleLinesAndStoresEveryLine() {
        givenSales(List.of(RoyaltyCalculatorTest.sale("CUSTOMER", 54, "1850.00", "0")));
        when(calculationRepository.saveAndFlush(any(RoyaltyCalculation.class))).thenAnswer(inv -> {
            RoyaltyCalculation c = inv.getArgument(0);
            c.setCalculationId(99L);
            return c;
        });

        RoyaltyCalculation result = service.calculate(request, 42L);

        assertThat(result.getBooksSold()).isEqualTo(54);
        assertThat(result.getGrossSales()).isEqualByComparingTo("99900.00");
        assertThat(result.getRoyaltyAmount()).isEqualByComparingTo("9990.00");
        assertThat(result.getPayableAmount()).isEqualByComparingTo("9990.00");
        assertThat(result.getStatus()).isEqualTo("CALCULATED");
        assertThat(result.getCalculatedBy()).isEqualTo(42L);
        verify(lineRepository).saveAll(any());
        verify(auditService).record(eq(42L), eq("ROYALTY_CALCULATED"), any(), eq(99L), any());
    }

    @Test
    void recoupedAdvanceIsAddedToTheAgreementsRunningTotal() {
        agreement.setAdvanceAmount(new BigDecimal("5000.00"));
        agreement.setAdvanceRecouped(new BigDecimal("1000.00"));
        givenSales(List.of(RoyaltyCalculatorTest.sale("CUSTOMER", 10, "1000.00", "0")));
        when(calculationRepository.saveAndFlush(any(RoyaltyCalculation.class))).thenAnswer(inv -> inv.getArgument(0));

        RoyaltyCalculation result = service.calculate(request, 42L);

        // royalty 1000, remaining advance 4000 -> all 1000 recouped, nothing payable
        assertThat(result.getAdvanceRecouped()).isEqualByComparingTo("1000.00");
        assertThat(result.getPayableAmount()).isEqualByComparingTo("0.00");
        assertThat(agreement.getAdvanceRecouped()).isEqualByComparingTo("2000.00");
    }

    @Test
    void rejectsCalculationWhenAgreementIsNotActive() {
        agreement.setStatus("DRAFT");

        assertThatThrownBy(() -> service.calculate(request, 42L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No active royalty agreement");

        verify(salesDataService, never()).getCompletedSaleLines(any(), any(), any());
        verify(calculationRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAPeriodOverlappingALiveCalculation() {
        RoyaltyCalculation existing = new RoyaltyCalculation();
        existing.setCalculationId(5L);
        existing.setSalesPeriodStart(LocalDate.of(2026, 7, 15));
        existing.setSalesPeriodEnd(LocalDate.of(2026, 8, 14));
        existing.setStatus("STATEMENT_ISSUED");
        when(calculationRepository.findOverlapping(7L, PERIOD_START, PERIOD_END)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.calculate(request, 42L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists")
                .hasMessageContaining("overlaps");

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
    void rejectsPeriodOutsideAgreementEffectiveRange() {
        request.setPeriodStart(LocalDate.of(2025, 12, 1));
        request.setPeriodEnd(LocalDate.of(2025, 12, 31));

        assertThatThrownBy(() -> service.calculate(request, 42L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("outside the agreement's effective range");
    }

    @Test
    void rejectsPeriodWithNoCompletedSales() {
        when(calculationRepository.findOverlapping(7L, PERIOD_START, PERIOD_END)).thenReturn(List.of());
        when(salesDataService.getCompletedSaleLines(1L, PERIOD_START, PERIOD_END)).thenReturn(List.of());

        assertThatThrownBy(() -> service.calculate(request, 42L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No completed sales");

        verify(calculationRepository, never()).saveAndFlush(any());
    }

    @Test
    void previewCalculatesWithoutSavingAnything() {
        givenSales(List.of(RoyaltyCalculatorTest.sale("CUSTOMER", 2, "1000.00", "0")));

        var preview = service.preview(request);

        assertThat(preview.isPreview()).isTrue();
        assertThat(preview.getCalculation().getRoyaltyAmount()).isEqualByComparingTo("200.00");
        verify(calculationRepository, never()).saveAndFlush(any());
        verify(lineRepository, never()).saveAll(any());
    }

    @Test
    void cancellingGivesTheRecoupedAdvanceBackAndFreesThePeriod() {
        agreement.setAdvanceRecouped(new BigDecimal("700.00"));
        RoyaltyCalculation calc = calc(11L, "CALCULATED");
        calc.setAdvanceRecouped(new BigDecimal("300.00"));
        when(calculationRepository.save(any(RoyaltyCalculation.class))).thenAnswer(inv -> inv.getArgument(0));

        RoyaltyCalculation result = service.cancel(11L, "Wrong period", 42L);

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        assertThat(result.getPeriodLock()).isNull();
        assertThat(result.getCancelReason()).isEqualTo("Wrong period");
        assertThat(agreement.getAdvanceRecouped()).isEqualByComparingTo("400.00");
    }

    @Test
    void onlyCalculatedCanBeCancelled() {
        calc(11L, "APPROVED");
        assertThatThrownBy(() -> service.cancel(11L, "x", 42L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only a CALCULATED royalty can be cancelled");
    }

    // ---------- statement / approval ----------

    @Test
    void issuingAStatementNumbersIt() {
        calc(11L, "CALCULATED");
        when(documentNumberService.next(eq("RS-"), eq("ROYALTY_STATEMENT"), any(Integer.class))).thenReturn("RS-2026-00001");
        when(calculationRepository.save(any(RoyaltyCalculation.class))).thenAnswer(inv -> inv.getArgument(0));

        RoyaltyCalculation result = service.issueStatement(11L, 42L);

        assertThat(result.getStatus()).isEqualTo("STATEMENT_ISSUED");
        assertThat(result.getStatementNumber()).isEqualTo("RS-2026-00001");
    }

    @Test
    void approvalCreatesThePaymentWhenPayableIsAboveThreshold() {
        RoyaltyCalculation calc = calc(11L, "STATEMENT_ISSUED");
        calc.setPayableAmount(new BigDecimal("5000.00"));
        when(settingsService.royaltyPaymentThreshold()).thenReturn(new BigDecimal("1000.00"));
        when(calculationRepository.saveAndFlush(any(RoyaltyCalculation.class))).thenAnswer(inv -> inv.getArgument(0));

        RoyaltyCalculation result = service.approve(11L, 42L);

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(result.getApprovedBy()).isEqualTo(42L);
        verify(royaltyPaymentService).createForApprovedCalculation(calc, 42L);
    }

    @Test
    void payableBelowThresholdIsCarriedForwardNotApproved() {
        RoyaltyCalculation calc = calc(11L, "STATEMENT_ISSUED");
        calc.setPayableAmount(new BigDecimal("400.00"));
        when(settingsService.royaltyPaymentThreshold()).thenReturn(new BigDecimal("1000.00"));
        when(calculationRepository.save(any(RoyaltyCalculation.class))).thenAnswer(inv -> inv.getArgument(0));

        RoyaltyCalculation result = service.approve(11L, 42L);

        assertThat(result.getStatus()).isEqualTo("CARRIED_FORWARD");
        verify(royaltyPaymentService, never()).createForApprovedCalculation(any(), anyLong());
    }

    @Test
    void onlyAnIssuedStatementCanBeApproved() {
        calc(11L, "CALCULATED");
        assertThatThrownBy(() -> service.approve(11L, 42L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expected status STATEMENT_ISSUED");
    }

    @Test
    void rejectionSendsTheStatementBackForCorrection() {
        calc(11L, "STATEMENT_ISSUED");
        when(calculationRepository.save(any(RoyaltyCalculation.class))).thenAnswer(inv -> inv.getArgument(0));

        RoyaltyCalculation result = service.reject(11L, "Check the returns", false, 42L);

        assertThat(result.getStatus()).isEqualTo("CALCULATED");
        assertThat(result.getRejectionReason()).isEqualTo("Check the returns");
    }

    // ---------- author ownership (US47) ----------

    @Test
    void authorCannotOpenAnotherAuthorsStatement() {
        when(authorDirectory.findByUserId(500L)).thenReturn(Optional.of(new AuthorDto(202L, "Other", "o@x.lk", 500L, "ACTIVE")));
        calc(11L, "APPROVED").setStatementNumber("RS-2026-00001");

        assertThatThrownBy(() -> service.getMyStatement(500L, 11L))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------- agreements (US41) ----------

    @Test
    void rejectsAgreementWhoseExpiryIsNotAfterEffectiveDate() {
        RoyaltyAgreementRequest r = agreementRequest();
        r.setExpiryDate(LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> service.createAgreement(r))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("must be after");
        verify(agreementRepository, never()).save(any());
    }

    @Test
    void rejectsAgreementForABookOfAnotherAuthor() {
        when(authorDirectory.findAuthor(101L)).thenReturn(Optional.of(new AuthorDto(101L, "A", "a@x.lk", 1L, "ACTIVE")));
        when(bookCatalog.findBook(1L)).thenReturn(Optional.of(book(1L, 999L)));

        assertThatThrownBy(() -> service.createAgreement(agreementRequest()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("belongs to author #999");
    }

    @Test
    void rejectsAgreementForUnknownAuthor() {
        when(authorDirectory.findAuthor(101L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createAgreement(agreementRequest()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Author #101 was not found");
    }

    @Test
    void createsDraftAgreementWithDefaults() {
        when(authorDirectory.findAuthor(101L)).thenReturn(Optional.of(new AuthorDto(101L, "A", "a@x.lk", 1L, "ACTIVE")));
        when(bookCatalog.findBook(1L)).thenReturn(Optional.of(book(1L, 101L)));
        when(agreementRepository.save(any(RoyaltyAgreement.class))).thenAnswer(inv -> inv.getArgument(0));

        RoyaltyAgreement saved = service.createAgreement(agreementRequest());

        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getBasis()).isEqualTo("NET_SALES");
        assertThat(saved.getPaymentFrequency()).isEqualTo("QUARTERLY");
        assertThat(saved.getAgreementNumber()).startsWith("RA-");
    }

    @Test
    void activationRejectsAnotherActiveAgreementForTheSameBookWithOverlappingDates() {
        agreement.setStatus("DRAFT");
        RoyaltyAgreement other = new RoyaltyAgreement();
        other.setRoyaltyAgreementId(8L);
        other.setAgreementNumber("RA-8");
        other.setBookId(1L);
        other.setAuthorId(303L);
        other.setEffectiveDate(LocalDate.of(2025, 6, 1));
        other.setExpiryDate(null);
        other.setStatus("ACTIVE");
        when(agreementRepository.findByBookIdAndStatus(1L, "ACTIVE")).thenReturn(List.of(other));

        assertThatThrownBy(() -> service.activateAgreement(7L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("overlaps");
    }

    @Test
    void activationAllowsNonOverlappingAgreementForTheSameBook() {
        agreement.setStatus("DRAFT");
        RoyaltyAgreement other = new RoyaltyAgreement();
        other.setRoyaltyAgreementId(8L);
        other.setBookId(1L);
        other.setEffectiveDate(LocalDate.of(2024, 1, 1));
        other.setExpiryDate(LocalDate.of(2025, 12, 31));
        other.setStatus("ACTIVE");
        when(agreementRepository.findByBookIdAndStatus(1L, "ACTIVE")).thenReturn(List.of(other));
        when(agreementRepository.save(any(RoyaltyAgreement.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.activateAgreement(7L).getStatus()).isEqualTo("ACTIVE");
    }

    // ---------- helpers ----------

    private void givenSales(List<SalesRecord> sales) {
        when(calculationRepository.findOverlapping(7L, PERIOD_START, PERIOD_END)).thenReturn(List.of());
        when(salesDataService.getCompletedSaleLines(1L, PERIOD_START, PERIOD_END)).thenReturn(sales);
        when(salesDataService.getReturnedSaleLines(1L, PERIOD_START, PERIOD_END)).thenReturn(List.of());
        when(calculationRepository.findByRoyaltyAgreementIdAndStatusAndCarriedIntoCalculationIdIsNull(7L, "CARRIED_FORWARD"))
                .thenReturn(List.of());
    }

    private RoyaltyCalculation calc(Long id, String status) {
        RoyaltyCalculation c = new RoyaltyCalculation();
        c.setCalculationId(id);
        c.setRoyaltyAgreementId(7L);
        c.setSalesPeriodStart(PERIOD_START);
        c.setSalesPeriodEnd(PERIOD_END);
        c.setStatus(status);
        lenient().when(calculationRepository.findById(id)).thenReturn(Optional.of(c));
        return c;
    }

    private static BookDto book(Long id, Long authorId) {
        return new BookDto(id, "Book", "978", authorId, 1L, 1L, new BigDecimal("1500.00"), null, "PUBLISHED");
    }

    private static RoyaltyAgreementRequest agreementRequest() {
        RoyaltyAgreementRequest r = new RoyaltyAgreementRequest();
        r.setAuthorId(101L);
        r.setBookId(1L);
        r.setRoyaltyPercentage(new BigDecimal("10.00"));
        r.setEffectiveDate(LocalDate.of(2026, 1, 1));
        return r;
    }
}

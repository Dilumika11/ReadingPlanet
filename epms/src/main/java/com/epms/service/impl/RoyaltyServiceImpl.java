package com.epms.service.impl;

import com.epms.contracts.AuthorDirectory;
import com.epms.contracts.BookCatalog;
import com.epms.contracts.dto.AuthorDto;
import com.epms.contracts.dto.BookDto;
import com.epms.dto.request.RoyaltyAgreementRequest;
import com.epms.dto.request.RoyaltyCalculationRequest;
import com.epms.dto.request.RoyaltyPayRequest;
import com.epms.dto.response.RoyaltyCalculationDetail;
import com.epms.dto.response.RoyaltyCalculationSummary;
import com.epms.dto.response.RoyaltyStatementResponse;
import com.epms.entity.RoyaltyAgreement;
import com.epms.entity.RoyaltyCalculation;
import com.epms.entity.RoyaltyCalculationLine;
import com.epms.entity.RoyaltyPayment;
import com.epms.entity.SalesRecord;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.InvalidRequestException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.RoyaltyAgreementRepository;
import com.epms.repository.RoyaltyCalculationLineRepository;
import com.epms.repository.RoyaltyCalculationRepository;
import com.epms.repository.RoyaltyPaymentRepository;
import com.epms.service.AuditService;
import com.epms.service.DocumentNumberService;
import com.epms.service.RoyaltyCalculator;
import com.epms.service.RoyaltyPaymentService;
import com.epms.service.RoyaltyService;
import com.epms.service.SalesDataService;
import com.epms.service.SettingsService;
import com.epms.validation.DateRanges;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class RoyaltyServiceImpl implements RoyaltyService {

    private static final String ACTIVE = "ACTIVE";
    private static final String DRAFT = "DRAFT";
    private static final String EXPIRED = "EXPIRED";

    private final RoyaltyCalculationRepository royaltyCalculationRepository;
    private final RoyaltyAgreementRepository royaltyAgreementRepository;
    private final RoyaltyCalculationLineRepository lineRepository;
    private final RoyaltyPaymentRepository royaltyPaymentRepository;
    private final SalesDataService salesDataService;
    private final AuthorDirectory authorDirectory;
    private final BookCatalog bookCatalog;
    private final SettingsService settingsService;
    private final DocumentNumberService documentNumberService;
    private final RoyaltyPaymentService royaltyPaymentService;
    private final AuditService auditService;

    // ===================== Agreements =====================

    @Override
    @Transactional(readOnly = true)
    public List<RoyaltyAgreement> getAllAgreements() {
        return royaltyAgreementRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public RoyaltyAgreement getAgreementById(Long id) {
        return royaltyAgreementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Royalty agreement not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoyaltyAgreement> getAgreementsByAuthor(Long authorId) {
        return royaltyAgreementRepository.findByAuthorId(authorId);
    }

    @Override
    public RoyaltyAgreement createAgreement(RoyaltyAgreementRequest request) {

        DateRanges.validateAgreementDates(request.getEffectiveDate(), request.getExpiryDate());
        validateAuthorAndBook(request.getAuthorId(), request.getBookId());

        RoyaltyAgreement agreement = new RoyaltyAgreement();
        applyTerms(agreement, request);
        agreement.setAgreementNumber("RA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        // Starts as DRAFT (entity default); use activateAgreement() to make it effective.

        return royaltyAgreementRepository.save(agreement);
    }

    @Override
    public RoyaltyAgreement updateAgreement(Long id, RoyaltyAgreementRequest request) {

        RoyaltyAgreement agreement = getAgreementById(id);
        if (!DRAFT.equals(agreement.getStatus())) {
            throw new BusinessRuleException("Only DRAFT agreements can be edited; agreement " + id + " is "
                    + agreement.getStatus());
        }
        DateRanges.validateAgreementDates(request.getEffectiveDate(), request.getExpiryDate());
        validateAuthorAndBook(request.getAuthorId(), request.getBookId());

        applyTerms(agreement, request);
        return royaltyAgreementRepository.save(agreement);
    }

    @Override
    public RoyaltyAgreement activateAgreement(Long id) {

        RoyaltyAgreement agreement = getAgreementById(id);

        if (ACTIVE.equalsIgnoreCase(agreement.getStatus())) {
            throw new BusinessRuleException("Royalty agreement " + id + " is already ACTIVE");
        }
        if (EXPIRED.equalsIgnoreCase(agreement.getStatus())) {
            throw new BusinessRuleException("Cannot activate an EXPIRED royalty agreement, create a new one");
        }

        Optional<RoyaltyAgreement> overlapping = royaltyAgreementRepository
                .findByBookIdAndStatus(agreement.getBookId(), ACTIVE)
                .stream()
                .filter(a -> !a.getRoyaltyAgreementId().equals(agreement.getRoyaltyAgreementId()))
                .filter(a -> overlaps(a.getEffectiveDate(), a.getExpiryDate(),
                        agreement.getEffectiveDate(), agreement.getExpiryDate()))
                .findFirst();
        if (overlapping.isPresent()) {
            RoyaltyAgreement other = overlapping.get();
            throw new BusinessRuleException("Book " + agreement.getBookId() + " already has an ACTIVE agreement ("
                    + other.getAgreementNumber() + ", " + other.getEffectiveDate() + " to "
                    + (other.getExpiryDate() == null ? "open" : other.getExpiryDate())
                    + ") that overlaps these dates");
        }

        agreement.setStatus(ACTIVE);
        return royaltyAgreementRepository.save(agreement);
    }

    @Override
    public RoyaltyAgreement expireAgreement(Long id) {

        RoyaltyAgreement agreement = getAgreementById(id);
        if (EXPIRED.equals(agreement.getStatus())) {
            throw new BusinessRuleException("Royalty agreement " + id + " is already EXPIRED");
        }
        agreement.setStatus(EXPIRED);

        return royaltyAgreementRepository.save(agreement);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuthorDto> getAuthors() {
        return authorDirectory.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookDto> getBooks(Long authorId) {
        return authorId == null ? bookCatalog.findAll() : bookCatalog.findByAuthor(authorId);
    }

    private void validateAuthorAndBook(Long authorId, Long bookId) {
        authorDirectory.findAuthor(authorId).orElseThrow(() ->
                new InvalidRequestException("Author #" + authorId + " was not found in the author directory"));
        BookDto book = bookCatalog.findBook(bookId).orElseThrow(() ->
                new InvalidRequestException("Book #" + bookId + " was not found in the book catalogue"));
        if (book.authorId() != null && !book.authorId().equals(authorId)) {
            throw new InvalidRequestException("Book #" + bookId + " (" + book.title() + ") belongs to author #"
                    + book.authorId() + ", not author #" + authorId);
        }
    }

    private static void applyTerms(RoyaltyAgreement agreement, RoyaltyAgreementRequest request) {
        agreement.setAuthorId(request.getAuthorId());
        agreement.setBookId(request.getBookId());
        agreement.setRoyaltyPercentage(request.getRoyaltyPercentage());
        agreement.setWholesaleRoyaltyPercentage(request.getWholesaleRoyaltyPercentage());
        agreement.setBasis(request.getBasis() == null ? "NET_SALES" : request.getBasis());
        agreement.setAdvanceAmount(request.getAdvanceAmount() == null
                ? BigDecimal.ZERO : RoyaltyCalculator.money(request.getAdvanceAmount()));
        agreement.setPaymentFrequency(request.getPaymentFrequency() == null ? "QUARTERLY" : request.getPaymentFrequency());
        agreement.setEffectiveDate(request.getEffectiveDate());
        agreement.setExpiryDate(request.getExpiryDate());
    }

    private static boolean overlaps(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
        boolean aStartsBeforeBEnds = bEnd == null || !aStart.isAfter(bEnd);
        boolean bStartsBeforeAEnds = aEnd == null || !bStart.isAfter(aEnd);
        return aStartsBeforeBEnds && bStartsBeforeAEnds;
    }

    // ===================== Calculations =====================

    @Override
    @Transactional(readOnly = true)
    public List<RoyaltyCalculationSummary> getAll(String status) {
        List<RoyaltyCalculation> calcs = status == null || status.isBlank()
                ? royaltyCalculationRepository.findAllByOrderByCalculatedAtDesc()
                : royaltyCalculationRepository.findByStatusOrderByCalculatedAtDesc(status.toUpperCase());
        return summarize(calcs);
    }

    @Override
    @Transactional(readOnly = true)
    public RoyaltyCalculation getById(Long id) {
        return royaltyCalculationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Royalty calculation not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public RoyaltyCalculationDetail getDetail(Long id) {
        RoyaltyCalculation calc = getById(id);
        RoyaltyAgreement agreement = getAgreementById(calc.getRoyaltyAgreementId());
        List<RoyaltyCalculationLine> lines = lineRepository.findByCalculationIdOrderBySaleDateAscLineIdAsc(id);
        return new RoyaltyCalculationDetail(false, calc, agreement, authorName(agreement.getAuthorId()),
                bookTitle(agreement.getBookId()), unitsByChannel(lines), lines);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoyaltyCalculation> getByAuthor(Long authorId) {

        List<Long> agreementIds = royaltyAgreementRepository.findByAuthorId(authorId)
                .stream()
                .map(RoyaltyAgreement::getRoyaltyAgreementId)
                .collect(Collectors.toList());

        if (agreementIds.isEmpty()) {
            return List.of();
        }

        return royaltyCalculationRepository.findByRoyaltyAgreementIdIn(agreementIds);
    }

    @Override
    @Transactional(readOnly = true)
    public RoyaltyCalculationDetail preview(RoyaltyCalculationRequest request) {
        Prepared p = prepare(request);
        RoyaltyCalculation calc = buildCalculation(p, request, null);
        return new RoyaltyCalculationDetail(true, calc, p.agreement, authorName(p.agreement.getAuthorId()),
                p.book == null ? null : p.book.title(), p.result.unitsByChannel(), p.result.lines());
    }

    @Override
    public RoyaltyCalculation calculate(RoyaltyCalculationRequest request, Long calculatedBy) {

        Prepared p = prepare(request);
        RoyaltyCalculation calculation = buildCalculation(p, request, calculatedBy);

        RoyaltyCalculation saved;
        try {
            // saveAndFlush so the unique key fires here, inside the
            // transaction, rather than at commit where we could not map it.
            saved = royaltyCalculationRepository.saveAndFlush(calculation);
        } catch (DataIntegrityViolationException ex) {
            // Backstop against a concurrent duplicate calculation racing
            // past the overlap check (the DB unique key wins).
            throw duplicate(p.agreement, request.getPeriodStart(), request.getPeriodEnd());
        }

        for (RoyaltyCalculationLine line : p.result.lines()) {
            line.setCalculationId(saved.getCalculationId());
        }
        lineRepository.saveAll(p.result.lines());

        for (RoyaltyCalculation carried : p.carriedForward) {
            carried.setCarriedIntoCalculationId(saved.getCalculationId());
            royaltyCalculationRepository.save(carried);
        }

        RoyaltyAgreement agreement = p.agreement;
        agreement.setAdvanceRecouped(agreement.getAdvanceRecouped().add(saved.getAdvanceRecouped()));
        royaltyAgreementRepository.save(agreement);

        auditService.record(calculatedBy, "ROYALTY_CALCULATED", "RoyaltyCalculation", saved.getCalculationId(),
                "Agreement " + agreement.getAgreementNumber() + ", " + saved.getSalesPeriodStart() + " to "
                        + saved.getSalesPeriodEnd() + ", gross royalty " + saved.getRoyaltyAmount()
                        + ", payable " + saved.getPayableAmount());
        return saved;
    }

    @Override
    public RoyaltyCalculation recalculate(Long calculationId, Long userId) {
        RoyaltyCalculation old = getById(calculationId);
        if (!RoyaltyCalculation.CALCULATED.equals(old.getStatus())) {
            throw new BusinessRuleException("Only a CALCULATED royalty can be recalculated; calculation "
                    + calculationId + " is " + old.getStatus());
        }
        cancel(calculationId, "Recalculated", userId);
        royaltyCalculationRepository.flush();

        RoyaltyCalculationRequest request = new RoyaltyCalculationRequest();
        request.setRoyaltyAgreementId(old.getRoyaltyAgreementId());
        request.setPeriodStart(old.getSalesPeriodStart());
        request.setPeriodEnd(old.getSalesPeriodEnd());
        request.setDeductions(old.getDeductions());
        return calculate(request, userId);
    }

    @Override
    public RoyaltyCalculation cancel(Long calculationId, String reason, Long userId) {
        RoyaltyCalculation calc = getById(calculationId);
        if (!RoyaltyCalculation.CALCULATED.equals(calc.getStatus())) {
            throw new BusinessRuleException("Only a CALCULATED royalty can be cancelled; calculation "
                    + calculationId + " is " + calc.getStatus()
                    + (RoyaltyCalculation.STATEMENT_ISSUED.equals(calc.getStatus())
                    ? " (reject the statement with 'cancel' instead)" : ""));
        }
        return doCancel(calc, reason, userId);
    }

    private RoyaltyCalculation doCancel(RoyaltyCalculation calc, String reason, Long userId) {
        requireReason(reason);

        // Give back the advance this calculation recouped, and release any
        // carried-forward amounts it absorbed so the next calculation picks them up.
        RoyaltyAgreement agreement = getAgreementById(calc.getRoyaltyAgreementId());
        agreement.setAdvanceRecouped(agreement.getAdvanceRecouped().subtract(calc.getAdvanceRecouped()).max(BigDecimal.ZERO));
        royaltyAgreementRepository.save(agreement);
        for (RoyaltyCalculation carried : royaltyCalculationRepository.findByCarriedIntoCalculationId(calc.getCalculationId())) {
            carried.setCarriedIntoCalculationId(null);
            royaltyCalculationRepository.save(carried);
        }

        calc.setStatus(RoyaltyCalculation.CANCELLED);
        calc.setPeriodLock(null);
        calc.setCancelReason(reason.trim());
        calc.setCancelledBy(userId);
        calc.setCancelledAt(LocalDateTime.now());
        RoyaltyCalculation saved = royaltyCalculationRepository.save(calc);

        auditService.record(userId, "ROYALTY_CANCELLED", "RoyaltyCalculation", calc.getCalculationId(), reason.trim());
        return saved;
    }

    /** Everything a calculation needs, validated, before anything is written. */
    private record Prepared(RoyaltyAgreement agreement, BookDto book, RoyaltyCalculator.Result result,
                            List<RoyaltyCalculation> carriedForward) {
    }

    private Prepared prepare(RoyaltyCalculationRequest request) {

        RoyaltyAgreement agreement = getAgreementById(request.getRoyaltyAgreementId());

        if (!ACTIVE.equalsIgnoreCase(agreement.getStatus())) {
            throw new BusinessRuleException(
                    "No active royalty agreement " + agreement.getRoyaltyAgreementId()
                            + " (status: " + agreement.getStatus() + "), royalty calculation cannot be finalized");
        }

        DateRanges.validateRoyaltyPeriod(request.getPeriodStart(), request.getPeriodEnd());

        if (request.getPeriodStart().isBefore(agreement.getEffectiveDate())
                || (agreement.getExpiryDate() != null && request.getPeriodEnd().isAfter(agreement.getExpiryDate()))) {
            throw new BusinessRuleException(
                    "Sales period is outside the agreement's effective range ("
                            + agreement.getEffectiveDate() + " to "
                            + (agreement.getExpiryDate() == null ? "open" : agreement.getExpiryDate()) + ")");
        }

        List<RoyaltyCalculation> overlapping = royaltyCalculationRepository.findOverlapping(
                agreement.getRoyaltyAgreementId(), request.getPeriodStart(), request.getPeriodEnd());
        if (!overlapping.isEmpty()) {
            RoyaltyCalculation other = overlapping.get(0);
            throw new BusinessRuleException(
                    "A royalty calculation already exists for agreement " + agreement.getRoyaltyAgreementId()
                            + " covering " + other.getSalesPeriodStart() + " to " + other.getSalesPeriodEnd()
                            + " (calculation #" + other.getCalculationId() + ", " + other.getStatus()
                            + "), which overlaps the requested period");
        }

        // Only COMPLETED sales from Epic 3 earn royalty.
        List<SalesRecord> sales = salesDataService.getCompletedSaleLines(
                agreement.getBookId(), request.getPeriodStart(), request.getPeriodEnd());
        if (sales.isEmpty()) {
            throw new BusinessRuleException(
                    "No completed sales found for book " + agreement.getBookId() + " between "
                            + request.getPeriodStart() + " and " + request.getPeriodEnd() + ", nothing to calculate");
        }
        List<SalesRecord> returns = salesDataService.getReturnedSaleLines(
                agreement.getBookId(), request.getPeriodStart(), request.getPeriodEnd());

        BookDto book = bookCatalog.findBook(agreement.getBookId()).orElse(null);

        List<RoyaltyCalculation> carried = royaltyCalculationRepository
                .findByRoyaltyAgreementIdAndStatusAndCarriedIntoCalculationIdIsNull(
                        agreement.getRoyaltyAgreementId(), RoyaltyCalculation.CARRIED_FORWARD);
        BigDecimal carriedIn = carried.stream().map(RoyaltyCalculation::getPayableAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        RoyaltyCalculator.Result result = RoyaltyCalculator.calculate(agreement,
                book == null ? null : book.listPrice(), sales, returns, request.getDeductions(), carriedIn);

        return new Prepared(agreement, book, result, carried);
    }

    private static RoyaltyCalculation buildCalculation(Prepared p, RoyaltyCalculationRequest request, Long calculatedBy) {
        RoyaltyAgreement agreement = p.agreement;
        RoyaltyCalculator.Result r = p.result;
        RoyaltyCalculation calculation = new RoyaltyCalculation();
        calculation.setRoyaltyAgreementId(agreement.getRoyaltyAgreementId());
        calculation.setSalesPeriodStart(request.getPeriodStart());
        calculation.setSalesPeriodEnd(request.getPeriodEnd());
        calculation.setBasis(agreement.getBasis());
        calculation.setRoyaltyRate(agreement.getRoyaltyPercentage());
        calculation.setWholesaleRate(agreement.getWholesaleRoyaltyPercentage());
        calculation.setBooksSold(r.booksSold());
        calculation.setUnitsReturned(r.unitsReturned());
        calculation.setReturnsAmount(r.returnsAmount());
        calculation.setGrossSales(r.grossSales());
        calculation.setDeductions(RoyaltyCalculator.money(
                request.getDeductions() == null ? BigDecimal.ZERO : request.getDeductions()));
        calculation.setRoyaltyBase(r.royaltyBase());
        calculation.setRoyaltyAmount(r.grossRoyalty());
        calculation.setAdvanceRecouped(r.advanceRecouped());
        calculation.setCarriedForwardIn(r.carriedForwardIn());
        calculation.setPayableAmount(r.payable());
        calculation.setStatus(RoyaltyCalculation.CALCULATED);
        calculation.setPeriodLock(Boolean.TRUE);
        calculation.setCalculatedBy(calculatedBy);
        calculation.setCalculatedAt(LocalDateTime.now());
        return calculation;
    }

    private static BusinessRuleException duplicate(RoyaltyAgreement agreement, LocalDate start, LocalDate end) {
        return new BusinessRuleException(
                "A royalty calculation already exists for agreement " + agreement.getRoyaltyAgreementId()
                        + " and period " + start + " to " + end);
    }

    // ===================== Statement / approval / payment =====================

    @Override
    public RoyaltyCalculation issueStatement(Long calculationId, Long userId) {
        RoyaltyCalculation calc = requireStatus(calculationId, RoyaltyCalculation.CALCULATED, "issue a statement for");

        if (calc.getStatementNumber() == null) {
            calc.setStatementNumber(documentNumberService.next("RS-", "ROYALTY_STATEMENT", LocalDate.now().getYear()));
        }
        calc.setStatus(RoyaltyCalculation.STATEMENT_ISSUED);
        calc.setStatementIssuedBy(userId);
        calc.setStatementIssuedAt(LocalDateTime.now());
        calc.setRejectionReason(null);
        RoyaltyCalculation saved = royaltyCalculationRepository.save(calc);

        auditService.record(userId, "ROYALTY_STATEMENT_ISSUED", "RoyaltyCalculation", calculationId,
                "Statement " + saved.getStatementNumber());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public RoyaltyStatementResponse getStatement(Long calculationId) {
        RoyaltyCalculation calc = getById(calculationId);
        if (calc.getStatementNumber() == null || RoyaltyCalculation.CALCULATED.equals(calc.getStatus())
                || calc.isCancelled()) {
            throw new BusinessRuleException("No statement has been issued for royalty calculation " + calculationId
                    + " (status " + calc.getStatus() + ")");
        }
        return buildStatement(calc);
    }

    @Override
    public RoyaltyCalculation approve(Long calculationId, Long userId) {
        RoyaltyCalculation calc = requireStatus(calculationId, RoyaltyCalculation.STATEMENT_ISSUED, "approve");

        BigDecimal threshold = settingsService.royaltyPaymentThreshold();
        calc.setApprovedBy(userId);
        calc.setApprovedAt(LocalDateTime.now());

        if (calc.getPayableAmount().signum() <= 0 || calc.getPayableAmount().compareTo(threshold) < 0) {
            // Too small to pay out now: carried into the next calculation for this agreement.
            calc.setStatus(RoyaltyCalculation.CARRIED_FORWARD);
            RoyaltyCalculation saved = royaltyCalculationRepository.save(calc);
            auditService.record(userId, "ROYALTY_CARRIED_FORWARD", "RoyaltyCalculation", calculationId,
                    "Payable " + calc.getPayableAmount() + " is below the payment threshold " + threshold);
            return saved;
        }

        calc.setStatus(RoyaltyCalculation.APPROVED);
        RoyaltyCalculation saved = royaltyCalculationRepository.saveAndFlush(calc);
        royaltyPaymentService.createForApprovedCalculation(saved, userId);

        auditService.record(userId, "ROYALTY_APPROVED", "RoyaltyCalculation", calculationId,
                "Payable " + calc.getPayableAmount() + " approved for payment");
        return saved;
    }

    @Override
    public RoyaltyCalculation reject(Long calculationId, String reason, boolean cancel, Long userId) {
        RoyaltyCalculation calc = requireStatus(calculationId, RoyaltyCalculation.STATEMENT_ISSUED, "reject");
        requireReason(reason);

        calc.setRejectionReason(reason.trim());
        auditService.record(userId, "ROYALTY_REJECTED", "RoyaltyCalculation", calculationId, reason.trim());
        if (cancel) {
            return doCancel(calc, reason, userId);
        }
        calc.setStatus(RoyaltyCalculation.CALCULATED);
        return royaltyCalculationRepository.save(calc);
    }

    @Override
    public RoyaltyPayment pay(Long calculationId, RoyaltyPayRequest request, Long userId) {
        RoyaltyCalculation calc = requireStatus(calculationId, RoyaltyCalculation.APPROVED, "pay");
        RoyaltyPayment payment = royaltyPaymentRepository.findByCalculationId(calc.getCalculationId())
                .orElseThrow(() -> new BusinessRuleException(
                        "Royalty calculation " + calculationId + " has no approved payment to record"));
        return royaltyPaymentService.markPaid(payment.getRoyaltyPaymentId(), request, userId);
    }

    private RoyaltyCalculation requireStatus(Long calculationId, String required, String action) {
        RoyaltyCalculation calc = getById(calculationId);
        if (!required.equals(calc.getStatus())) {
            throw new BusinessRuleException("Cannot " + action + " royalty calculation " + calculationId
                    + ": expected status " + required + " but was " + calc.getStatus());
        }
        return calc;
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException("A reason is required");
        }
    }

    // ===================== Author self-service =====================

    @Override
    @Transactional(readOnly = true)
    public List<RoyaltyAgreement> getMyAgreements(Long userId) {
        return royaltyAgreementRepository.findByAuthorId(currentAuthor(userId).authorId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoyaltyCalculationSummary> getMyStatements(Long userId) {
        List<RoyaltyCalculation> issued = getByAuthor(currentAuthor(userId).authorId()).stream()
                .filter(RoyaltyServiceImpl::hasIssuedStatement)
                .sorted((a, b) -> b.getSalesPeriodEnd().compareTo(a.getSalesPeriodEnd()))
                .collect(Collectors.toList());
        return summarize(issued);
    }

    @Override
    @Transactional(readOnly = true)
    public RoyaltyStatementResponse getMyStatement(Long userId, Long calculationId) {
        AuthorDto author = currentAuthor(userId);
        RoyaltyCalculation calc = getById(calculationId);
        RoyaltyAgreement agreement = getAgreementById(calc.getRoyaltyAgreementId());
        if (!author.authorId().equals(agreement.getAuthorId())) {
            throw new AccessDeniedException("You can only view your own royalty statements");
        }
        if (!hasIssuedStatement(calc)) {
            throw new ResourceNotFoundException("Royalty statement not found: " + calculationId);
        }
        return buildStatement(calc);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoyaltyPayment> getMyPayments(Long userId) {
        List<Long> agreementIds = getMyAgreements(userId).stream()
                .map(RoyaltyAgreement::getRoyaltyAgreementId).collect(Collectors.toList());
        if (agreementIds.isEmpty()) {
            return List.of();
        }
        return royaltyPaymentRepository.findByRoyaltyAgreementIdInOrderByPaymentDateDesc(agreementIds);
    }

    private AuthorDto currentAuthor(Long userId) {
        return authorDirectory.findByUserId(userId).orElseThrow(() ->
                new ResourceNotFoundException("No author profile is linked to this account"));
    }

    private static boolean hasIssuedStatement(RoyaltyCalculation c) {
        return c.getStatementNumber() != null
                && !RoyaltyCalculation.CALCULATED.equals(c.getStatus())
                && !c.isCancelled();
    }

    // ===================== Helpers =====================

    private RoyaltyStatementResponse buildStatement(RoyaltyCalculation calc) {
        RoyaltyAgreement agreement = getAgreementById(calc.getRoyaltyAgreementId());
        Optional<AuthorDto> author = authorDirectory.findAuthor(agreement.getAuthorId());
        Optional<BookDto> book = bookCatalog.findBook(agreement.getBookId());
        List<RoyaltyCalculationLine> lines = lineRepository.findByCalculationIdOrderBySaleDateAscLineIdAsc(
                calc.getCalculationId());

        RoyaltyStatementResponse s = new RoyaltyStatementResponse();
        s.setCalculationId(calc.getCalculationId());
        s.setStatementNumber(calc.getStatementNumber());
        s.setIssuedAt(calc.getStatementIssuedAt());
        s.setStatus(calc.getStatus());
        s.setCompanyName(settingsService.companyName());
        s.setCompanyAddress(settingsService.companyAddress());
        s.setCurrency(settingsService.currency());
        s.setAuthorId(agreement.getAuthorId());
        s.setAuthorName(author.map(AuthorDto::fullName).orElse("Author #" + agreement.getAuthorId()));
        s.setAuthorEmail(author.map(AuthorDto::email).orElse(null));
        s.setBookId(agreement.getBookId());
        s.setBookTitle(book.map(BookDto::title).orElse("Book #" + agreement.getBookId()));
        s.setIsbn(book.map(BookDto::isbn).orElse(null));
        s.setPeriodStart(calc.getSalesPeriodStart());
        s.setPeriodEnd(calc.getSalesPeriodEnd());
        s.setAgreementNumber(agreement.getAgreementNumber());
        s.setBasis(calc.getBasis() != null ? calc.getBasis() : agreement.getBasis());
        s.setRoyaltyRate(calc.getRoyaltyRate() != null ? calc.getRoyaltyRate() : agreement.getRoyaltyPercentage());
        s.setWholesaleRate(calc.getWholesaleRate());
        s.setPaymentFrequency(agreement.getPaymentFrequency());
        s.setAdvanceAmount(agreement.getAdvanceAmount());
        s.setUnitsByChannel(unitsByChannel(lines));
        s.setUnitsSold(calc.getBooksSold());
        s.setUnitsReturned(calc.getUnitsReturned());
        s.setReturnsAmount(calc.getReturnsAmount());
        s.setGrossSales(calc.getGrossSales());
        s.setDeductions(calc.getDeductions());
        s.setRoyaltyBase(calc.getRoyaltyBase());
        s.setGrossRoyalty(calc.getRoyaltyAmount());
        s.setAdvanceRecouped(calc.getAdvanceRecouped());
        s.setCarriedForwardIn(calc.getCarriedForwardIn());
        s.setPayableAmount(calc.getPayableAmount());
        royaltyPaymentRepository.findByCalculationId(calc.getCalculationId())
                .filter(p -> "PAID".equals(p.getPaymentStatus()))
                .ifPresent(p -> {
                    s.setPaidOn(p.getPaymentDate());
                    s.setPaymentMethod(p.getPaymentMethod());
                    s.setPaymentReference(p.getTransactionReference());
                });
        s.setLines(lines);
        return s;
    }

    private static Map<String, Integer> unitsByChannel(List<RoyaltyCalculationLine> lines) {
        return lines.stream()
                .filter(l -> RoyaltyCalculationLine.SALE.equals(l.getLineType()))
                .collect(Collectors.groupingBy(RoyaltyCalculationLine::getChannel, TreeMap::new,
                        Collectors.summingInt(RoyaltyCalculationLine::getQuantity)));
    }

    private List<RoyaltyCalculationSummary> summarize(Collection<RoyaltyCalculation> calcs) {
        if (calcs.isEmpty()) {
            return List.of();
        }
        Map<Long, RoyaltyAgreement> agreements = royaltyAgreementRepository.findAllById(
                        calcs.stream().map(RoyaltyCalculation::getRoyaltyAgreementId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(RoyaltyAgreement::getRoyaltyAgreementId, Function.identity()));
        Map<Long, String> authorNames = authorDirectory.findAll().stream()
                .collect(Collectors.toMap(AuthorDto::authorId, AuthorDto::fullName, (a, b) -> a));
        Map<Long, String> bookTitles = bookCatalog.findAll().stream()
                .collect(Collectors.toMap(BookDto::bookId, BookDto::title, (a, b) -> a));

        return calcs.stream().map(c -> {
            RoyaltyAgreement a = agreements.get(c.getRoyaltyAgreementId());
            Long authorId = a == null ? null : a.getAuthorId();
            Long bookId = a == null ? null : a.getBookId();
            return new RoyaltyCalculationSummary(c,
                    a == null ? null : a.getAgreementNumber(),
                    authorId, authorId == null ? null : authorNames.getOrDefault(authorId, "Author #" + authorId),
                    bookId, bookId == null ? null : bookTitles.getOrDefault(bookId, "Book #" + bookId));
        }).collect(Collectors.toList());
    }

    private String authorName(Long authorId) {
        return authorDirectory.findAuthor(authorId).map(AuthorDto::fullName).orElse("Author #" + authorId);
    }

    private String bookTitle(Long bookId) {
        return bookCatalog.findBook(bookId).map(BookDto::title).orElse("Book #" + bookId);
    }
}

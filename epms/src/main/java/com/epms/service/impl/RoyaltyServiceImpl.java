package com.epms.service.impl;

import com.epms.dto.request.RoyaltyAgreementRequest;
import com.epms.dto.request.RoyaltyCalculationRequest;
import com.epms.dto.response.SalesSummaryResponse;
import com.epms.entity.RoyaltyAgreement;
import com.epms.entity.RoyaltyCalculation;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.RoyaltyAgreementRepository;
import com.epms.repository.RoyaltyCalculationRepository;
import com.epms.service.RoyaltyService;
import com.epms.service.SalesDataService;
import com.epms.validation.DateRanges;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class RoyaltyServiceImpl implements RoyaltyService {

    private final RoyaltyCalculationRepository royaltyCalculationRepository;
    private final RoyaltyAgreementRepository royaltyAgreementRepository;
    private final SalesDataService salesDataService;

    // --- Agreements ---

    @Override
    public List<RoyaltyAgreement> getAllAgreements() {
        return royaltyAgreementRepository.findAll();
    }

    @Override
    public RoyaltyAgreement getAgreementById(Long id) {
        return royaltyAgreementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Royalty agreement not found: " + id));
    }

    @Override
    public List<RoyaltyAgreement> getAgreementsByAuthor(Long authorId) {
        return royaltyAgreementRepository.findByAuthorId(authorId);
    }

    @Override
    public RoyaltyAgreement createAgreement(RoyaltyAgreementRequest request) {

        DateRanges.validateAgreementDates(request.getEffectiveDate(), request.getExpiryDate());

        RoyaltyAgreement agreement = new RoyaltyAgreement();
        agreement.setAuthorId(request.getAuthorId());
        agreement.setBookId(request.getBookId());
        agreement.setRoyaltyPercentage(request.getRoyaltyPercentage());
        agreement.setEffectiveDate(request.getEffectiveDate());
        agreement.setExpiryDate(request.getExpiryDate());
        agreement.setAgreementNumber("RA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        // Starts as DRAFT (entity default); use activateAgreement() to make it effective.

        return royaltyAgreementRepository.save(agreement);
    }

    @Override
    public RoyaltyAgreement activateAgreement(Long id) {

        RoyaltyAgreement agreement = getAgreementById(id);

        if ("ACTIVE".equalsIgnoreCase(agreement.getStatus())) {
            throw new BusinessRuleException("Royalty agreement " + id + " is already ACTIVE");
        }
        if ("EXPIRED".equalsIgnoreCase(agreement.getStatus())) {
            throw new BusinessRuleException("Cannot activate an EXPIRED royalty agreement — create a new one");
        }

        boolean alreadyActiveForBook = royaltyAgreementRepository
                .findByBookIdAndStatus(agreement.getBookId(), "ACTIVE")
                .stream()
                .anyMatch(a -> a.getAuthorId().equals(agreement.getAuthorId()));

        if (alreadyActiveForBook) {
            throw new BusinessRuleException(
                    "Author " + agreement.getAuthorId() + " already has an ACTIVE agreement for book "
                            + agreement.getBookId());
        }

        agreement.setStatus("ACTIVE");
        return royaltyAgreementRepository.save(agreement);
    }

    @Override
    public RoyaltyAgreement expireAgreement(Long id) {

        RoyaltyAgreement agreement = getAgreementById(id);
        agreement.setStatus("EXPIRED");

        return royaltyAgreementRepository.save(agreement);
    }

    // --- Calculations ---

    @Override
    public List<RoyaltyCalculation> getAll() {
        return royaltyCalculationRepository.findAll();
    }

    @Override
    public RoyaltyCalculation getById(Long id) {
        return royaltyCalculationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Royalty calculation not found: " + id));
    }

    @Override
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
    public RoyaltyCalculation calculate(RoyaltyCalculationRequest request, Long calculatedBy) {

        RoyaltyAgreement agreement = getAgreementById(request.getRoyaltyAgreementId());

        if (!"ACTIVE".equalsIgnoreCase(agreement.getStatus())) {
            throw new BusinessRuleException(
                    "No active royalty agreement " + agreement.getRoyaltyAgreementId()
                            + " (status: " + agreement.getStatus() + ") — royalty calculation cannot be finalized");
        }

        DateRanges.validateRoyaltyPeriod(request.getPeriodStart(), request.getPeriodEnd());

        if (request.getPeriodStart().isBefore(agreement.getEffectiveDate())
                || (agreement.getExpiryDate() != null && request.getPeriodEnd().isAfter(agreement.getExpiryDate()))) {
            throw new BusinessRuleException(
                    "Sales period is outside the agreement's effective range ("
                            + agreement.getEffectiveDate() + " to "
                            + (agreement.getExpiryDate() == null ? "open" : agreement.getExpiryDate()) + ")");
        }

        if (royaltyCalculationRepository.existsByRoyaltyAgreementIdAndSalesPeriodStartAndSalesPeriodEnd(
                agreement.getRoyaltyAgreementId(), request.getPeriodStart(), request.getPeriodEnd())) {
            throw new BusinessRuleException(
                    "A royalty calculation already exists for agreement " + agreement.getRoyaltyAgreementId()
                            + " and period " + request.getPeriodStart() + " to " + request.getPeriodEnd());
        }

        // Only COMPLETED sales from Epic 3 count — cancelled/returned are excluded upstream.
        SalesSummaryResponse sales = salesDataService.getCompletedSalesForBook(
                agreement.getBookId(), request.getPeriodStart(), request.getPeriodEnd());

        if (sales.getBooksSold() == 0) {
            throw new BusinessRuleException(
                    "No completed sales found for book " + agreement.getBookId() + " between "
                            + request.getPeriodStart() + " and " + request.getPeriodEnd() + " — nothing to calculate");
        }

        BigDecimal deductions = request.getDeductions() == null ? BigDecimal.ZERO : request.getDeductions();
        if (deductions.compareTo(sales.getGrossSales()) > 0) {
            throw new BusinessRuleException(
                    "Deductions " + deductions + " exceed gross sales " + sales.getGrossSales() + " for the period");
        }

        BigDecimal royaltyBase = sales.getGrossSales().subtract(deductions);
        BigDecimal royaltyAmount = royaltyBase
                .multiply(agreement.getRoyaltyPercentage())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        RoyaltyCalculation calculation = new RoyaltyCalculation();
        calculation.setRoyaltyAgreementId(agreement.getRoyaltyAgreementId());
        calculation.setSalesPeriodStart(request.getPeriodStart());
        calculation.setSalesPeriodEnd(request.getPeriodEnd());
        calculation.setBooksSold(sales.getBooksSold());
        calculation.setGrossSales(sales.getGrossSales());
        calculation.setDeductions(deductions);
        calculation.setRoyaltyBase(royaltyBase);
        calculation.setRoyaltyAmount(royaltyAmount);
        calculation.setStatus("CALCULATED");
        calculation.setCalculatedBy(calculatedBy);
        calculation.setCalculatedAt(LocalDateTime.now());

        try {
            // saveAndFlush so the unique constraint fires here, inside the
            // transaction, rather than at commit where we could not map it.
            return royaltyCalculationRepository.saveAndFlush(calculation);
        } catch (DataIntegrityViolationException ex) {
            // Backstop against a concurrent duplicate calculation racing
            // past the existsBy check above (DB unique constraint wins).
            throw new BusinessRuleException(
                    "A royalty calculation already exists for agreement " + agreement.getRoyaltyAgreementId()
                            + " and period " + request.getPeriodStart() + " to " + request.getPeriodEnd());
        }
    }

    @Override
    public RoyaltyCalculation recalculate(Long calculationId) {
        throw new UnsupportedOperationException(
                "Royalty recalculation not yet implemented — requires authorization + audit trail, "
                        + "see docs/epic-4-spec.pdf section 39 rule 8");
    }
}

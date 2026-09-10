package com.epms.service.impl;

import com.epms.dto.request.RoyaltyAgreementRequest;
import com.epms.dto.request.RoyaltyCalculationRequest;
import com.epms.entity.RoyaltyAgreement;
import com.epms.entity.RoyaltyCalculation;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.RoyaltyAgreementRepository;
import com.epms.repository.RoyaltyCalculationRepository;
import com.epms.service.RoyaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoyaltyServiceImpl implements RoyaltyService {

    private final RoyaltyCalculationRepository royaltyCalculationRepository;
    private final RoyaltyAgreementRepository royaltyAgreementRepository;

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

        BigDecimal deductions = request.getDeductions() == null ? BigDecimal.ZERO : request.getDeductions();
        BigDecimal royaltyBase = request.getGrossSales().subtract(deductions);
        BigDecimal royaltyAmount = royaltyBase
                .multiply(agreement.getRoyaltyPercentage())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        RoyaltyCalculation calculation = new RoyaltyCalculation();
        calculation.setRoyaltyAgreementId(agreement.getRoyaltyAgreementId());
        calculation.setSalesPeriodStart(request.getPeriodStart());
        calculation.setSalesPeriodEnd(request.getPeriodEnd());
        calculation.setBooksSold(request.getBooksSold());
        calculation.setGrossSales(request.getGrossSales());
        calculation.setDeductions(deductions);
        calculation.setRoyaltyBase(royaltyBase);
        calculation.setRoyaltyAmount(royaltyAmount);
        calculation.setStatus("CALCULATED");
        calculation.setCalculatedBy(calculatedBy);
        calculation.setCalculatedAt(LocalDateTime.now());

        try {
            return royaltyCalculationRepository.save(calculation);
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

package com.epms.service.impl;

import com.epms.entity.RoyaltyAgreement;
import com.epms.entity.RoyaltyCalculation;
import com.epms.repository.RoyaltyAgreementRepository;
import com.epms.repository.RoyaltyCalculationRepository;
import com.epms.service.RoyaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoyaltyServiceImpl implements RoyaltyService {

    private final RoyaltyCalculationRepository royaltyCalculationRepository;
    private final RoyaltyAgreementRepository royaltyAgreementRepository;

    @Override
    public List<RoyaltyCalculation> getAll() {
        return royaltyCalculationRepository.findAll();
    }

    @Override
    public RoyaltyCalculation getById(Long id) {
        return royaltyCalculationRepository.findById(id)
                .orElseThrow(() -> new com.epms.exception.ResourceNotFoundException(
                        "Royalty calculation not found: " + id));
    }

    @Override
    public List<RoyaltyCalculation> getByAuthor(Long authorId) {
        // TODO: requires resolving the author's royalty agreements first,
        // then their calculations. See docs/epic-4-spec.pdf section 30.
        throw new UnsupportedOperationException(
                "Royalty-by-author lookup not yet implemented — see docs/epic-4-spec.pdf section 30");
    }

    @Override
    public RoyaltyCalculation calculate(Long royaltyAgreementId, LocalDate periodStart, LocalDate periodEnd) {
        // TODO: pull completed sales for [periodStart, periodEnd] from
        // Epic 3, apply the agreement's royalty_percentage using
        // BigDecimal, and reject if a finalized calculation already
        // exists for this (agreement, period) — see the unique
        // constraint uq_royalty_calc_period in docs/epms-schema.sql and
        // the "Royalty Calculation Integrity" section of the spec.
        throw new UnsupportedOperationException(
                "Royalty calculation engine not yet implemented — see docs/epic-4-spec.pdf sections 16, 40-42");
    }

    @Override
    public RoyaltyCalculation recalculate(Long calculationId) {
        throw new UnsupportedOperationException(
                "Royalty recalculation not yet implemented — requires authorization + audit trail, "
                        + "see docs/epic-4-spec.pdf section 39 rule 8");
    }

    @Override
    public List<RoyaltyAgreement> getAgreementsByAuthor(Long authorId) {
        return royaltyAgreementRepository.findByAuthorId(authorId);
    }
}

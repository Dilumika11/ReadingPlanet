package com.epms.service;

import com.epms.dto.request.RoyaltyAgreementRequest;
import com.epms.dto.request.RoyaltyCalculationRequest;
import com.epms.entity.RoyaltyAgreement;
import com.epms.entity.RoyaltyCalculation;

import java.util.List;

public interface RoyaltyService {

    // --- Agreements: DRAFT -> ACTIVE -> EXPIRED ---

    List<RoyaltyAgreement> getAllAgreements();

    RoyaltyAgreement getAgreementById(Long id);

    List<RoyaltyAgreement> getAgreementsByAuthor(Long authorId);

    RoyaltyAgreement createAgreement(RoyaltyAgreementRequest request);

    RoyaltyAgreement activateAgreement(Long id);

    RoyaltyAgreement expireAgreement(Long id);

    // --- Calculations ---

    List<RoyaltyCalculation> getAll();

    RoyaltyCalculation getById(Long id);

    List<RoyaltyCalculation> getByAuthor(Long authorId);

    /**
     * Validates the agreement is ACTIVE and covers the given period,
     * rejects a duplicate calculation for the same agreement + period,
     * then computes royaltyBase = grossSales - deductions and
     * royaltyAmount = royaltyBase * (royaltyPercentage / 100) using
     * BigDecimal.
     *
     * NOTE: sales figures are supplied directly in the request as an
     * interim substitute for pulling completed sales from Epic 3 (no
     * API contract exists yet) — see docs/epic-4-spec.pdf section 52.
     */
    RoyaltyCalculation calculate(RoyaltyCalculationRequest request, Long calculatedBy);

    /**
     * Recalculation must be explicitly authorized and produce an audit
     * record — it must not silently overwrite a finalized calculation.
     */
    RoyaltyCalculation recalculate(Long calculationId);
}

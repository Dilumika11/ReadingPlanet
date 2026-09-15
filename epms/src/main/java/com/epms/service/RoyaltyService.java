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
     * Books sold and gross sales are pulled from the COMPLETED sales
     * received from Epic 3 (via SalesDataService) for the agreement's book
     * over the period — see docs/epic-4-spec.pdf section 52. A period with
     * no completed sales is rejected rather than producing a zero statement.
     */
    RoyaltyCalculation calculate(RoyaltyCalculationRequest request, Long calculatedBy);

    /**
     * Recalculation must be explicitly authorized and produce an audit
     * record — it must not silently overwrite a finalized calculation.
     */
    RoyaltyCalculation recalculate(Long calculationId);
}

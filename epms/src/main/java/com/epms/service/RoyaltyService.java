package com.epms.service;

import com.epms.entity.RoyaltyAgreement;
import com.epms.entity.RoyaltyCalculation;

import java.time.LocalDate;
import java.util.List;

/**
 * TODO (Epic 4): royalty calculation engine.
 * See docs/epic-4-spec.pdf sections 16-17, 35, 39-42 for the business
 * rules that must be implemented here — completed-sales-only basis,
 * one finalized calculation per (agreement, sales period), BigDecimal
 * arithmetic, historical immutability.
 */
public interface RoyaltyService {

    List<RoyaltyCalculation> getAll();

    RoyaltyCalculation getById(Long id);

    List<RoyaltyCalculation> getByAuthor(Long authorId);

    /**
     * Identifies the active RoyaltyAgreement, pulls completed sales for
     * the given period from Epic 3, applies the agreement's rate, and
     * stores the result. Must reject if a finalized calculation already
     * exists for the same agreement + period (duplicate prevention).
     */
    RoyaltyCalculation calculate(Long royaltyAgreementId, LocalDate periodStart, LocalDate periodEnd);

    /**
     * Recalculation must be explicitly authorized and produce an audit
     * record — it must not silently overwrite a finalized calculation.
     */
    RoyaltyCalculation recalculate(Long calculationId);

    List<RoyaltyAgreement> getAgreementsByAuthor(Long authorId);
}

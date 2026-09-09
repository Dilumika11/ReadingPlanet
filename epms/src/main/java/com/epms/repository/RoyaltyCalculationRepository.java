package com.epms.repository;

import com.epms.entity.RoyaltyCalculation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoyaltyCalculationRepository extends JpaRepository<RoyaltyCalculation, Long> {

    boolean existsByRoyaltyAgreementIdAndSalesPeriodStartAndSalesPeriodEnd(
            Long royaltyAgreementId, LocalDate salesPeriodStart, LocalDate salesPeriodEnd
    );

    Optional<RoyaltyCalculation> findByRoyaltyAgreementIdAndSalesPeriodStartAndSalesPeriodEnd(
            Long royaltyAgreementId, LocalDate salesPeriodStart, LocalDate salesPeriodEnd
    );

    List<RoyaltyCalculation> findByRoyaltyAgreementId(Long royaltyAgreementId);
}

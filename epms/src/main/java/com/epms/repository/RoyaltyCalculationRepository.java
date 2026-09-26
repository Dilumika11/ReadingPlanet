package com.epms.repository;

import com.epms.entity.RoyaltyCalculation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface RoyaltyCalculationRepository extends JpaRepository<RoyaltyCalculation, Long> {

    /** Live (not cancelled) calculations for the agreement whose period overlaps [start, end]. */
    @Query("SELECT c FROM RoyaltyCalculation c WHERE c.royaltyAgreementId = :agreementId"
            + " AND c.status <> 'CANCELLED'"
            + " AND c.salesPeriodStart <= :end AND c.salesPeriodEnd >= :start")
    List<RoyaltyCalculation> findOverlapping(@Param("agreementId") Long agreementId,
                                             @Param("start") LocalDate start,
                                             @Param("end") LocalDate end);

    List<RoyaltyCalculation> findByRoyaltyAgreementId(Long royaltyAgreementId);

    List<RoyaltyCalculation> findByRoyaltyAgreementIdIn(Collection<Long> royaltyAgreementIds);

    List<RoyaltyCalculation> findByStatusOrderByCalculatedAtDesc(String status);

    List<RoyaltyCalculation> findByStatusIn(Collection<String> statuses);

    List<RoyaltyCalculation> findAllByOrderByCalculatedAtDesc();

    List<RoyaltyCalculation> findByRoyaltyAgreementIdAndStatusAndCarriedIntoCalculationIdIsNull(
            Long royaltyAgreementId, String status);

    List<RoyaltyCalculation> findByCarriedIntoCalculationId(Long calculationId);

    long countByStatus(String status);
}

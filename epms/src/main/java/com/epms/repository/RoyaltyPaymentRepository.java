package com.epms.repository;

import com.epms.entity.RoyaltyPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoyaltyPaymentRepository extends JpaRepository<RoyaltyPayment, Long> {

    boolean existsByPaymentReference(String paymentReference);

    Optional<RoyaltyPayment> findByCalculationId(Long calculationId);

    List<RoyaltyPayment> findByRoyaltyAgreementId(Long royaltyAgreementId);
}

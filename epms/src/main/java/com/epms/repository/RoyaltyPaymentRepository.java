package com.epms.repository;

import com.epms.entity.RoyaltyPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoyaltyPaymentRepository extends JpaRepository<RoyaltyPayment, Long> {

    boolean existsByPaymentReference(String paymentReference);

    boolean existsByTransactionReference(String transactionReference);

    Optional<RoyaltyPayment> findByCalculationId(Long calculationId);

    List<RoyaltyPayment> findByRoyaltyAgreementId(Long royaltyAgreementId);

    List<RoyaltyPayment> findByRoyaltyAgreementIdInOrderByPaymentDateDesc(Collection<Long> agreementIds);

    List<RoyaltyPayment> findByPaymentStatusAndPaymentDateBetween(String paymentStatus, LocalDate from, LocalDate to);

    List<RoyaltyPayment> findAllByOrderByCreatedAtDesc();
}

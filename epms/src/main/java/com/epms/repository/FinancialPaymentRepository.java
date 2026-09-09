package com.epms.repository;

import com.epms.entity.FinancialPayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialPaymentRepository extends JpaRepository<FinancialPayment, Long> {

    boolean existsByReferenceNumber(String referenceNumber);
}

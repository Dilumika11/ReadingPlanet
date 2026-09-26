package com.epms.repository;

import com.epms.entity.FinancialPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinancialPaymentRepository extends JpaRepository<FinancialPayment, Long> {

    boolean existsByReferenceNumber(String referenceNumber);

    List<FinancialPayment> findByInvoiceIdOrderByPaymentDateAsc(Long invoiceId);

    List<FinancialPayment> findAllByOrderByPaymentDateDesc();
}

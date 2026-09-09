package com.epms.repository;

import com.epms.entity.FinancialInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialInvoiceRepository extends JpaRepository<FinancialInvoice, Long> {

    boolean existsByInvoiceNumber(String invoiceNumber);
}

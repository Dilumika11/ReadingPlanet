package com.epms.repository;

import com.epms.entity.FinancialInvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinancialInvoiceLineRepository extends JpaRepository<FinancialInvoiceLine, Long> {

    List<FinancialInvoiceLine> findByInvoiceIdOrderByLineNo(Long invoiceId);

    void deleteByInvoiceId(Long invoiceId);
}

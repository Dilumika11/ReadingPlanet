package com.epms.repository;

import com.epms.entity.FinancialReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinancialReportRepository extends JpaRepository<FinancialReport, Long> {

    List<FinancialReport> findAllByOrderByGeneratedDateDesc();
}

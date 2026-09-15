package com.epms.repository;

import com.epms.entity.SalesRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SalesRecordRepository extends JpaRepository<SalesRecord, Long> {

    List<SalesRecord> findBySaleDateBetweenOrderBySaleDateDesc(LocalDate from, LocalDate to);

    List<SalesRecord> findByStatusAndSaleDateBetweenOrderBySaleDateDesc(String status, LocalDate from, LocalDate to);

    List<SalesRecord> findByBookIdAndStatusAndSaleDateBetween(Long bookId, String status, LocalDate from, LocalDate to);
}

package com.epms.repository;

import com.epms.entity.RoyaltyCalculationLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoyaltyCalculationLineRepository extends JpaRepository<RoyaltyCalculationLine, Long> {

    List<RoyaltyCalculationLine> findByCalculationIdOrderBySaleDateAscLineIdAsc(Long calculationId);
}

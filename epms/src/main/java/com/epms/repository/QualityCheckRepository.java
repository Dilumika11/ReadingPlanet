package com.epms.repository;

import com.epms.entity.QualityCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QualityCheckRepository extends JpaRepository<QualityCheck, Long> {

    List<QualityCheck> findByManuscriptIdOrderByCheckedAtDesc(Long manuscriptId);
}

package com.epms.repository;

import com.epms.entity.ManuscriptStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ManuscriptStatusHistoryRepository extends JpaRepository<ManuscriptStatusHistory, Long> {

    List<ManuscriptStatusHistory> findByManuscriptIdOrderByChangedAtAscHistoryIdAsc(Long manuscriptId);
}

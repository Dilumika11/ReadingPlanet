package com.epms.repository;

import com.epms.entity.ManuscriptRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManuscriptRevisionRepository extends JpaRepository<ManuscriptRevision, Long> {

    List<ManuscriptRevision> findByManuscriptIdOrderByRevisionRoundAsc(Long manuscriptId);

    Optional<ManuscriptRevision> findFirstByManuscriptIdAndStatusOrderByRevisionRoundDesc(Long manuscriptId, String status);

    long countByManuscriptId(Long manuscriptId);
}

package com.epms.repository;

import com.epms.entity.DesignAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DesignAssignmentRepository extends JpaRepository<DesignAssignment, Long> {

    Optional<DesignAssignment> findFirstByManuscriptIdAndAssignmentStatus(Long manuscriptId, String status);

    List<DesignAssignment> findByDesignerIdAndAssignmentStatusIn(Long designerId, Collection<String> statuses);

    List<DesignAssignment> findByAssignmentStatusOrderByAssignedAtDesc(String status);

    List<DesignAssignment> findByManuscriptIdOrderByAssignedAtDesc(Long manuscriptId);
}

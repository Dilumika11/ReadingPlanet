package com.epms.repository;

import com.epms.entity.Manuscript;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ManuscriptRepository extends JpaRepository<Manuscript, Long> {

    List<Manuscript> findByAuthorIdOrderByUpdatedAtDesc(Long authorId);

    List<Manuscript> findByAssignedEditorIdOrderByUpdatedAtDesc(Long editorId);

    List<Manuscript> findByStatusInOrderByUpdatedAtDesc(Collection<String> statuses);

    List<Manuscript> findAllByOrderByUpdatedAtDesc();

    long countByStatus(String status);
}

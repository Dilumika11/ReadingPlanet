package com.epms.repository;

import com.epms.entity.EditorialReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EditorialReviewRepository extends JpaRepository<EditorialReview, Long> {

    List<EditorialReview> findByManuscriptIdOrderByReviewRoundAsc(Long manuscriptId);

    long countByManuscriptId(Long manuscriptId);
}

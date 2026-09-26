package com.epms.repository;

import com.epms.entity.AuthorApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AuthorApprovalRepository extends JpaRepository<AuthorApproval, Long> {

    List<AuthorApproval> findByDesignIdInOrderByReviewedAtDesc(Collection<Long> designIds);
}

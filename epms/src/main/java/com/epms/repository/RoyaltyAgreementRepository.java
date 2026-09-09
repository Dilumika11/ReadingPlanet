package com.epms.repository;

import com.epms.entity.RoyaltyAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoyaltyAgreementRepository extends JpaRepository<RoyaltyAgreement, Long> {

    List<RoyaltyAgreement> findByAuthorId(Long authorId);

    Optional<RoyaltyAgreement> findFirstByAuthorIdAndBookIdAndStatusOrderByEffectiveDateDesc(
            Long authorId, Long bookId, String status
    );

    List<RoyaltyAgreement> findByBookIdAndStatus(Long bookId, String status);
}

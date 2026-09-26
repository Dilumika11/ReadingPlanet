package com.epms.repository;

import com.epms.entity.AuthorBankDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthorBankDetailsRepository extends JpaRepository<AuthorBankDetails, Long> {

    Optional<AuthorBankDetails> findByAuthorId(Long authorId);
}

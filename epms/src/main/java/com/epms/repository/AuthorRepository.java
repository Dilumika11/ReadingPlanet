package com.epms.repository;

import com.epms.entity.Author;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    Optional<Author> findByUserId(Long userId);

    boolean existsByAuthorCode(String authorCode);
}

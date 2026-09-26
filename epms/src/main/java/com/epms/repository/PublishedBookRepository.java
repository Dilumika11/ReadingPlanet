package com.epms.repository;

import com.epms.entity.PublishedBook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PublishedBookRepository extends JpaRepository<PublishedBook, Long> {

    Optional<PublishedBook> findByManuscriptId(Long manuscriptId);

    boolean existsByIsbn(String isbn);

    List<PublishedBook> findByBookStatusInOrderByTitle(Collection<String> statuses);
}

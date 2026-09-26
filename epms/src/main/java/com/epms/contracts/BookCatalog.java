package com.epms.contracts;

import com.epms.contracts.dto.BookDto;

import java.util.List;
import java.util.Optional;

/** Epic 4's only way to read the published book catalogue owned by Epics 2/3. */
public interface BookCatalog {

    Optional<BookDto> findBook(Long bookId);

    List<BookDto> findByAuthor(Long authorId);

    List<BookDto> findAll();
}

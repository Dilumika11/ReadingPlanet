package com.epms.contracts.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Minimum book fields Epic 4 needs from Epics 2/3 (see docs/INTEGRATION_CONTRACT.md). */
public record BookDto(Long bookId, String title, String isbn, Long authorId, Long categoryId, Long genreId,
                      BigDecimal listPrice, LocalDate publishedDate, String status) {
}

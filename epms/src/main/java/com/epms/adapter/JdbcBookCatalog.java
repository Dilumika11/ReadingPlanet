package com.epms.adapter;

import com.epms.contracts.BookCatalog;
import com.epms.contracts.dto.BookDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.List;
import java.util.Optional;

/**
 * Reads the published {@code books} table read-only. A book's author comes
 * from the manuscript it was produced from (books.manuscript_id ->
 * manuscripts.author_id). If the tables are missing the catalogue behaves as
 * empty and logs a warning.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcBookCatalog implements BookCatalog {

    private static final String SELECT =
            "SELECT b.book_id, b.title, b.isbn, m.author_id, b.category_id, b.genre_id, b.price, "
            + " b.publication_date, b.book_status "
            + "FROM books b LEFT JOIN manuscripts m ON m.manuscript_id = b.manuscript_id ";

    private static final RowMapper<BookDto> MAPPER = (rs, i) -> {
        Date published = rs.getDate("publication_date");
        Long authorId = rs.getObject("author_id", Long.class);
        return new BookDto(
                rs.getLong("book_id"),
                rs.getString("title"),
                rs.getString("isbn"),
                authorId,
                rs.getObject("category_id", Long.class),
                rs.getObject("genre_id", Long.class),
                rs.getBigDecimal("price"),
                published == null ? null : published.toLocalDate(),
                rs.getString("book_status"));
    };

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<BookDto> findBook(Long bookId) {
        return query(SELECT + "WHERE b.book_id = ?", bookId).stream().findFirst();
    }

    @Override
    public List<BookDto> findByAuthor(Long authorId) {
        return query(SELECT + "WHERE m.author_id = ? ORDER BY b.title", authorId);
    }

    @Override
    public List<BookDto> findAll() {
        return query(SELECT + "ORDER BY b.title");
    }

    private List<BookDto> query(String sql, Object... args) {
        try {
            return jdbcTemplate.query(sql, MAPPER, args);
        } catch (DataAccessException e) {
            log.warn("Book catalogue data is not available ({}); treating the catalogue as empty",
                    e.getMostSpecificCause().getMessage());
            return List.of();
        }
    }
}

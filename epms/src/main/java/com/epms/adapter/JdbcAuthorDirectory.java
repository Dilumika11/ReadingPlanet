package com.epms.adapter;

import com.epms.contracts.AuthorDirectory;
import com.epms.contracts.dto.AuthorDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Reads Epic 1's {@code authors} table (joined to {@code users} for name and
 * email) read-only. If Epic 1's table is missing the directory behaves as
 * empty and logs a warning, so Epic 4 screens still load.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcAuthorDirectory implements AuthorDirectory {

    private static final String SELECT =
            "SELECT a.author_id, a.user_id, a.status, "
            + " COALESCE(NULLIF(a.pen_name, ''), u.full_name) AS full_name, u.email "
            + "FROM authors a JOIN users u ON u.user_id = a.user_id ";

    private static final RowMapper<AuthorDto> MAPPER = (rs, i) -> new AuthorDto(
            rs.getLong("author_id"),
            rs.getString("full_name"),
            rs.getString("email"),
            rs.getLong("user_id"),
            rs.getString("status"));

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<AuthorDto> findAuthor(Long authorId) {
        return query(SELECT + "WHERE a.author_id = ?", authorId).stream().findFirst();
    }

    @Override
    public Optional<AuthorDto> findByUserId(Long userId) {
        return query(SELECT + "WHERE a.user_id = ?", userId).stream().findFirst();
    }

    @Override
    public List<AuthorDto> findAll() {
        return query(SELECT + "ORDER BY full_name");
    }

    private List<AuthorDto> query(String sql, Object... args) {
        try {
            return jdbcTemplate.query(sql, MAPPER, args);
        } catch (DataAccessException e) {
            log.warn("Epic 1 author data is not available ({}); treating the author directory as empty",
                    e.getMostSpecificCause().getMessage());
            return List.of();
        }
    }
}

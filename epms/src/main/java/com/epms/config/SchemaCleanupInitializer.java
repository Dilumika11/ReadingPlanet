package com.epms.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Removes columns that no longer exist in the entity model but linger in a
 * database created by an earlier version (Hibernate's ddl-auto=update adds
 * columns but never drops them, and a leftover NOT NULL column blocks
 * inserts). Each drop is attempted once and silently skipped if the column
 * is already gone. Runs before the demo-data seeders.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class SchemaCleanupInitializer implements CommandLineRunner {

    // table -> column dropped when archive/status was replaced by plain CRUD
    private static final List<String[]> OBSOLETE_COLUMNS = List.of(
            new String[] {"categories", "status"},
            new String[] {"genres", "status"},
            new String[] {"announcements", "status"},
            new String[] {"catalog_books", "status"}
    );

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        for (String[] tc : OBSOLETE_COLUMNS) {
            if (!columnExists(tc[0], tc[1])) {
                continue;
            }
            try {
                jdbcTemplate.execute("ALTER TABLE " + tc[0] + " DROP COLUMN " + tc[1]);
                log.info("Schema cleanup: dropped obsolete column {}.{}", tc[0], tc[1]);
            } catch (Exception e) {
                log.warn("Schema cleanup: could not drop {}.{}: {}", tc[0], tc[1], e.getMessage());
            }
        }
    }

    private boolean columnExists(String table, String column) {
        try {
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = ? AND UPPER(COLUMN_NAME) = ?",
                    Integer.class, table.toUpperCase(), column.toUpperCase());
            return n != null && n > 0;
        } catch (Exception e) {
            return false;
        }
    }
}

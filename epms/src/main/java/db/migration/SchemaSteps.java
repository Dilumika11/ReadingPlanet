package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Idempotent DDL helpers shared by the Java migrations. Every step checks
 * the live schema first, so a migration can run on databases created at
 * different times (MySQL has no "ADD COLUMN IF NOT EXISTS").
 * Not a migration itself.
 */
final class SchemaSteps {

    private final Connection c;

    SchemaSteps(Connection c) {
        this.c = c;
    }

    void requireBaseSchema(String... tables) throws SQLException {
        for (String t : tables) {
            if (!tableExists(t)) {
                throw new IllegalStateException("Table '" + t + "' is missing. Create the database with "
                        + "docs/epms-schema.sql first, then start the app again.");
            }
        }
    }

    void exec(String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    void addColumn(String table, String column, String definition) throws SQLException {
        if (!columnExists(table, column)) {
            exec("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    void renameColumn(String table, String from, String to, String definition) throws SQLException {
        if (columnExists(table, from) && !columnExists(table, to)) {
            exec("ALTER TABLE " + table + " CHANGE " + from + " " + to + " " + definition);
        }
    }

    void makeNullable(String table, String column, String type) throws SQLException {
        if (columnExists(table, column) && !columnNullable(table, column)) {
            exec("ALTER TABLE " + table + " MODIFY " + column + " " + type + " NULL");
        }
    }

    void addUniqueIndex(String table, String index, String columns) throws SQLException {
        if (!indexExists(table, index)) {
            exec("CREATE UNIQUE INDEX " + index + " ON " + table + " (" + columns + ")");
        }
    }

    void addIndex(String table, String index, String columns) throws SQLException {
        if (!indexExists(table, index)) {
            exec("CREATE INDEX " + index + " ON " + table + " (" + columns + ")");
        }
    }

    boolean tableExists(String table) throws SQLException {
        return count("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                table) > 0;
    }

    boolean columnExists(String table, String column) throws SQLException {
        return count("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                + " AND TABLE_NAME = ? AND COLUMN_NAME = ?", table, column) > 0;
    }

    boolean columnNullable(String table, String column) throws SQLException {
        return count("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                + " AND TABLE_NAME = ? AND COLUMN_NAME = ? AND IS_NULLABLE = 'YES'", table, column) > 0;
    }

    boolean indexExists(String table, String index) throws SQLException {
        return count("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()"
                + " AND TABLE_NAME = ? AND INDEX_NAME = ?", table, index) > 0;
    }

    private int count(String sql, String... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}

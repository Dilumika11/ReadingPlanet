package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Epic 4 schema additions on top of docs/epms-schema.sql.
 *
 * Written as a Java migration (not plain SQL) so every step checks the live
 * schema first: team members' databases were created at different times
 * (some by Hibernate ddl-auto=update), and MySQL has no
 * "ADD COLUMN IF NOT EXISTS". Each step is skipped when already applied,
 * so the migration is safe on any of those databases.
 */
public class V400__Epic4_finance_and_royalty extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();

        if (!tableExists(c, "royalty_agreements") || !tableExists(c, "users")) {
            throw new IllegalStateException(
                    "The EPMS base schema is missing. Create the database with docs/epms-schema.sql first, then start the app again.");
        }

        // audit_logs is part of the shared core schema; create it if this database predates it.
        exec(c, "CREATE TABLE IF NOT EXISTS audit_logs ("
                + " audit_log_id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + " user_id BIGINT,"
                + " action VARCHAR(100) NOT NULL,"
                + " entity_name VARCHAR(100) NOT NULL,"
                + " entity_id BIGINT,"
                + " details TEXT,"
                + " ip_address VARCHAR(45),"
                + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB");

        // Genres are managed without a parent category in the admin UI; the
        // base schema's NOT NULL category_id blocked creating any genre.
        if (columnExists(c, "genres", "category_id") && !columnNullable(c, "genres", "category_id")) {
            exec(c, "ALTER TABLE genres MODIFY category_id BIGINT NULL");
        }

        // --- Announcements: audience (ALL or a role name) ---
        addColumn(c, "announcements", "audience", "VARCHAR(30) NOT NULL DEFAULT 'ALL'");

        // --- Sales feed from Epic 3: per-line discount (net = sale_amount - discount) ---
        addColumn(c, "sales_records", "discount", "DECIMAL(12,2) NOT NULL DEFAULT 0.00");

        // --- Royalty agreements: terms needed by the calculation engine ---
        addColumn(c, "royalty_agreements", "basis", "VARCHAR(20) NOT NULL DEFAULT 'NET_SALES'");
        addColumn(c, "royalty_agreements", "wholesale_royalty_percentage", "DECIMAL(5,2) NULL");
        addColumn(c, "royalty_agreements", "advance_amount", "DECIMAL(12,2) NOT NULL DEFAULT 0.00");
        addColumn(c, "royalty_agreements", "advance_recouped", "DECIMAL(12,2) NOT NULL DEFAULT 0.00");
        addColumn(c, "royalty_agreements", "payment_frequency", "VARCHAR(20) NOT NULL DEFAULT 'QUARTERLY'");
        addColumn(c, "royalty_agreements", "version", "BIGINT NOT NULL DEFAULT 0");

        // --- Royalty calculations: statement / approval / payment workflow ---
        addColumn(c, "royalty_calculations", "basis", "VARCHAR(20) NULL");
        addColumn(c, "royalty_calculations", "royalty_rate", "DECIMAL(5,2) NULL");
        addColumn(c, "royalty_calculations", "wholesale_rate", "DECIMAL(5,2) NULL");
        addColumn(c, "royalty_calculations", "units_returned", "INT NOT NULL DEFAULT 0");
        addColumn(c, "royalty_calculations", "returns_amount", "DECIMAL(14,2) NOT NULL DEFAULT 0.00");
        addColumn(c, "royalty_calculations", "advance_recouped", "DECIMAL(14,2) NOT NULL DEFAULT 0.00");
        addColumn(c, "royalty_calculations", "carried_forward_in", "DECIMAL(14,2) NOT NULL DEFAULT 0.00");
        addColumn(c, "royalty_calculations", "payable_amount", "DECIMAL(14,2) NOT NULL DEFAULT 0.00");
        addColumn(c, "royalty_calculations", "carried_into_calculation_id", "BIGINT NULL");
        addColumn(c, "royalty_calculations", "statement_number", "VARCHAR(30) NULL");
        addColumn(c, "royalty_calculations", "statement_issued_by", "BIGINT NULL");
        addColumn(c, "royalty_calculations", "statement_issued_at", "TIMESTAMP NULL");
        addColumn(c, "royalty_calculations", "approved_by", "BIGINT NULL");
        addColumn(c, "royalty_calculations", "approved_at", "TIMESTAMP NULL");
        addColumn(c, "royalty_calculations", "rejection_reason", "VARCHAR(500) NULL");
        addColumn(c, "royalty_calculations", "cancel_reason", "VARCHAR(500) NULL");
        addColumn(c, "royalty_calculations", "cancelled_by", "BIGINT NULL");
        addColumn(c, "royalty_calculations", "cancelled_at", "TIMESTAMP NULL");
        addColumn(c, "royalty_calculations", "version", "BIGINT NOT NULL DEFAULT 0");
        // 1 while the calculation is live, NULL once cancelled: the unique key
        // below then blocks duplicates but frees the period after a cancel.
        addColumn(c, "royalty_calculations", "period_lock", "TINYINT(1) NULL DEFAULT 1");
        if (!indexExists(c, "royalty_calculations", "uq_royalty_calc_active_period")) {
            exec(c, "CREATE UNIQUE INDEX uq_royalty_calc_active_period ON royalty_calculations"
                    + " (royalty_agreement_id, sales_period_start, sales_period_end, period_lock)");
        }
        if (indexExists(c, "royalty_calculations", "uq_royalty_calc_period")) {
            exec(c, "ALTER TABLE royalty_calculations DROP INDEX uq_royalty_calc_period");
        }
        if (!indexExists(c, "royalty_calculations", "uq_royalty_statement_number")) {
            exec(c, "CREATE UNIQUE INDEX uq_royalty_statement_number ON royalty_calculations (statement_number)");
        }

        exec(c, "CREATE TABLE IF NOT EXISTS royalty_calculation_lines ("
                + " line_id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + " calculation_id BIGINT NOT NULL,"
                + " sale_id BIGINT,"
                + " sale_reference VARCHAR(40),"
                + " sale_date DATE NOT NULL,"
                + " channel VARCHAR(20) NOT NULL,"
                + " line_type VARCHAR(20) NOT NULL,"
                + " quantity INT NOT NULL,"
                + " unit_price DECIMAL(12,2) NOT NULL,"
                + " discount DECIMAL(12,2) NOT NULL DEFAULT 0.00,"
                + " base_amount DECIMAL(14,2) NOT NULL,"
                + " rate_applied DECIMAL(5,2) NOT NULL,"
                + " royalty_amount DECIMAL(14,2) NOT NULL,"
                + " INDEX idx_royalty_lines_calc (calculation_id),"
                + " CONSTRAINT fk_royalty_lines_calc FOREIGN KEY (calculation_id)"
                + "   REFERENCES royalty_calculations(calculation_id) ON UPDATE CASCADE ON DELETE CASCADE"
                + ") ENGINE=InnoDB");

        // --- Royalty payments: bank/cheque reference entered when paid ---
        addColumn(c, "royalty_payments", "transaction_reference", "VARCHAR(100) NULL");
        addColumn(c, "royalty_payments", "version", "BIGINT NOT NULL DEFAULT 0");
        addColumn(c, "royalty_payments", "processed_by", "BIGINT NULL");
        if (!indexExists(c, "royalty_payments", "uq_royalty_payment_txn_reference")) {
            exec(c, "CREATE UNIQUE INDEX uq_royalty_payment_txn_reference ON royalty_payments (transaction_reference)");
        }

        // --- Expenses: review / reject / receipt / source link ---
        addColumn(c, "expenses", "reviewed_by", "BIGINT NULL");
        addColumn(c, "expenses", "rejection_reason", "VARCHAR(500) NULL");
        addColumn(c, "expenses", "receipt_file", "VARCHAR(255) NULL");
        addColumn(c, "expenses", "receipt_content_type", "VARCHAR(100) NULL");
        addColumn(c, "expenses", "source_reference", "VARCHAR(60) NULL");

        // --- Invoices: customer, tax snapshot, line items, payment tracking ---
        addColumn(c, "financial_invoices", "customer_name", "VARCHAR(150) NULL");
        addColumn(c, "financial_invoices", "customer_email", "VARCHAR(150) NULL");
        addColumn(c, "financial_invoices", "customer_address", "VARCHAR(255) NULL");
        addColumn(c, "financial_invoices", "currency", "VARCHAR(10) NOT NULL DEFAULT 'LKR'");
        addColumn(c, "financial_invoices", "tax_rate", "DECIMAL(5,2) NOT NULL DEFAULT 0.00");
        addColumn(c, "financial_invoices", "amount_paid", "DECIMAL(12,2) NOT NULL DEFAULT 0.00");
        addColumn(c, "financial_invoices", "due_date", "DATE NULL");
        addColumn(c, "financial_invoices", "notes", "VARCHAR(500) NULL");
        addColumn(c, "financial_invoices", "created_by", "BIGINT NULL");
        addColumn(c, "financial_invoices", "issued_at", "TIMESTAMP NULL");
        addColumn(c, "financial_invoices", "cancel_reason", "VARCHAR(255) NULL");
        addColumn(c, "financial_invoices", "version", "BIGINT NOT NULL DEFAULT 0");

        exec(c, "CREATE TABLE IF NOT EXISTS financial_invoice_lines ("
                + " line_id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + " invoice_id BIGINT NOT NULL,"
                + " line_no INT NOT NULL,"
                + " description VARCHAR(255) NOT NULL,"
                + " quantity INT NOT NULL,"
                + " unit_price DECIMAL(12,2) NOT NULL,"
                + " line_total DECIMAL(12,2) NOT NULL,"
                + " INDEX idx_invoice_lines_invoice (invoice_id),"
                + " CONSTRAINT fk_invoice_lines_invoice FOREIGN KEY (invoice_id)"
                + "   REFERENCES financial_invoices(invoice_id) ON UPDATE CASCADE ON DELETE CASCADE"
                + ") ENGINE=InnoDB");

        addColumn(c, "financial_payments", "invoice_id", "BIGINT NULL");
        addColumn(c, "financial_payments", "recorded_by", "BIGINT NULL");

        // --- Financial reports: stored snapshot + review/finalize attribution ---
        addColumn(c, "financial_reports", "snapshot_json", "TEXT NULL");
        addColumn(c, "financial_reports", "currency", "VARCHAR(10) NULL");
        addColumn(c, "financial_reports", "reviewed_by", "BIGINT NULL");
        addColumn(c, "financial_reports", "reviewed_at", "TIMESTAMP NULL");
        addColumn(c, "financial_reports", "finalized_by", "BIGINT NULL");
        addColumn(c, "financial_reports", "finalized_at", "TIMESTAMP NULL");

        // --- Gap-free-per-key counters for invoice / statement / payment numbers ---
        exec(c, "CREATE TABLE IF NOT EXISTS document_sequences ("
                + " sequence_key VARCHAR(40) PRIMARY KEY,"
                + " next_value BIGINT NOT NULL"
                + ") ENGINE=InnoDB");
    }

    private static void addColumn(Connection c, String table, String column, String definition) throws SQLException {
        if (!columnExists(c, table, column)) {
            exec(c, "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private static void exec(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private static boolean tableExists(Connection c, String table) throws SQLException {
        return count(c, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                table) > 0;
    }

    private static boolean columnExists(Connection c, String table, String column) throws SQLException {
        return count(c, "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                + " AND TABLE_NAME = ? AND COLUMN_NAME = ?", table, column) > 0;
    }

    private static boolean columnNullable(Connection c, String table, String column) throws SQLException {
        return count(c, "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                + " AND TABLE_NAME = ? AND COLUMN_NAME = ? AND IS_NULLABLE = 'YES'", table, column) > 0;
    }

    private static boolean indexExists(Connection c, String table, String index) throws SQLException {
        return count(c, "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()"
                + " AND TABLE_NAME = ? AND INDEX_NAME = ?", table, index) > 0;
    }

    private static int count(Connection c, String sql, String... params) throws SQLException {
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

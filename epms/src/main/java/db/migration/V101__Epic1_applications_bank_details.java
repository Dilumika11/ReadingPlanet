package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Epic 1 additions from the team's merged build: "Getting Published"
 * applications, author bank details and author finance notifications, plus
 * the shared-core password reset and login activity tables if missing.
 */
public class V101__Epic1_applications_bank_details extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        SchemaSteps s = new SchemaSteps(context.getConnection());
        s.requireBaseSchema("users", "authors");

        s.exec("CREATE TABLE IF NOT EXISTS publishing_applications ("
                + " application_id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + " author_name VARCHAR(150) NOT NULL,"
                + " contact_information VARCHAR(500),"
                + " phone VARCHAR(30) NOT NULL,"
                + " email VARCHAR(255) NOT NULL,"
                + " manuscript_name VARCHAR(255) NOT NULL,"
                + " book_type VARCHAR(100) NOT NULL,"
                + " short_description TEXT,"
                + " file_name VARCHAR(255),"
                + " file_path VARCHAR(500),"
                + " file_type VARCHAR(150),"
                + " file_size BIGINT,"
                + " status VARCHAR(30) NOT NULL DEFAULT 'PENDING',"
                + " submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + " approved_at TIMESTAMP NULL,"
                + " reviewed_by BIGINT NULL,"
                + " author_user_id BIGINT NULL,"
                + " rejection_reason TEXT,"
                + " INDEX idx_pub_app_email (email)"
                + ") ENGINE=InnoDB");
        s.addColumn("publishing_applications", "reviewed_by", "BIGINT NULL");

        s.exec("CREATE TABLE IF NOT EXISTS author_bank_details ("
                + " bank_detail_id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + " author_id BIGINT NOT NULL,"
                + " account_name VARCHAR(150) NOT NULL,"
                + " bank_name VARCHAR(150) NOT NULL,"
                + " account_number VARCHAR(100) NOT NULL,"
                + " branch VARCHAR(150),"
                + " updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + " CONSTRAINT uq_author_bank_author UNIQUE (author_id),"
                + " CONSTRAINT fk_author_bank_author FOREIGN KEY (author_id) REFERENCES authors(author_id)"
                + "   ON UPDATE CASCADE ON DELETE CASCADE"
                + ") ENGINE=InnoDB");

        s.exec("CREATE TABLE IF NOT EXISTS author_finance_notifications ("
                + " notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + " user_id BIGINT NOT NULL,"
                + " type VARCHAR(50) NOT NULL,"
                + " title VARCHAR(255) NOT NULL,"
                + " message TEXT NOT NULL,"
                + " is_read TINYINT(1) NOT NULL DEFAULT 0,"
                + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + " INDEX idx_author_notifications_user (user_id)"
                + ") ENGINE=InnoDB");

        // Shared core tables (present in docs/epms-schema.sql; created here for older databases)
        s.exec("CREATE TABLE IF NOT EXISTS password_reset_tokens ("
                + " token_id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + " user_id BIGINT NOT NULL,"
                + " token VARCHAR(255) NOT NULL,"
                + " expires_at TIMESTAMP NOT NULL,"
                + " used_at TIMESTAMP NULL,"
                + " is_used BOOLEAN NOT NULL DEFAULT FALSE,"
                + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + " CONSTRAINT uq_password_reset_token UNIQUE (token)"
                + ") ENGINE=InnoDB");
        s.exec("CREATE TABLE IF NOT EXISTS user_sessions ("
                + " session_id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + " user_id BIGINT NOT NULL,"
                + " session_token VARCHAR(255) NOT NULL,"
                + " ip_address VARCHAR(45) NOT NULL,"
                + " user_agent VARCHAR(500),"
                + " login_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + " last_activity_at TIMESTAMP NULL,"
                + " expires_at TIMESTAMP NOT NULL,"
                + " logout_at TIMESTAMP NULL,"
                + " is_active BOOLEAN NOT NULL DEFAULT TRUE,"
                + " CONSTRAINT uq_user_sessions_token UNIQUE (session_token)"
                + ") ENGINE=InnoDB");
    }
}

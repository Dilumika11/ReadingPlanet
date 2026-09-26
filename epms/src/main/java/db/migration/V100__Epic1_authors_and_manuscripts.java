package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Epic 1 (Author & Manuscript Management) additions on top of docs/epms-schema.sql. */
public class V100__Epic1_authors_and_manuscripts extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        SchemaSteps s = new SchemaSteps(context.getConnection());
        s.requireBaseSchema("users", "authors", "manuscripts", "manuscript_files", "manuscript_revisions");

        // Files: what kind of file, who uploaded it, the stored name on disk
        s.addColumn("manuscript_files", "file_category", "VARCHAR(20) NOT NULL DEFAULT 'MANUSCRIPT'");
        s.addColumn("manuscript_files", "uploaded_by", "BIGINT NULL");
        s.addColumn("manuscript_files", "notes", "VARCHAR(500) NULL");

        // The author's answer to a revision request
        s.addColumn("manuscript_revisions", "response_notes", "TEXT NULL");
        s.addColumn("manuscript_revisions", "response_file_id", "BIGINT NULL");

        // Every status change, for tracking (US4) and monitoring (US15)
        s.exec("CREATE TABLE IF NOT EXISTS manuscript_status_history ("
                + " history_id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + " manuscript_id BIGINT NOT NULL,"
                + " from_status VARCHAR(30),"
                + " to_status VARCHAR(30) NOT NULL,"
                + " changed_by BIGINT,"
                + " remarks VARCHAR(500),"
                + " changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + " INDEX idx_ms_history_manuscript (manuscript_id),"
                + " CONSTRAINT fk_ms_history_manuscript FOREIGN KEY (manuscript_id)"
                + "   REFERENCES manuscripts(manuscript_id) ON UPDATE CASCADE ON DELETE CASCADE"
                + ") ENGINE=InnoDB");
    }
}

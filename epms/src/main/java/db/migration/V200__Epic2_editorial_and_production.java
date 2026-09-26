package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Epic 2 (Editorial Workflow & Book Production) additions on top of docs/epms-schema.sql. */
public class V200__Epic2_editorial_and_production extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        SchemaSteps s = new SchemaSteps(context.getConnection());
        s.requireBaseSchema("manuscripts", "editorial_reviews", "book_designs", "author_approvals", "books");

        // The editor responsible for a manuscript (US11)
        s.addColumn("manuscripts", "assigned_editor_id", "BIGINT NULL");
        s.addColumn("manuscripts", "assigned_by", "BIGINT NULL");
        s.addColumn("manuscripts", "assigned_at", "TIMESTAMP NULL");
        s.addIndex("manuscripts", "idx_manuscripts_editor", "assigned_editor_id");

        // Print-ready PDF alongside the cover and interior layout (US17)
        s.addColumn("book_designs", "print_file_path", "VARCHAR(500) NULL");
        s.addColumn("book_designs", "submitted_at", "TIMESTAMP NULL");

        // Production quality checks (US19)
        s.exec("CREATE TABLE IF NOT EXISTS production_quality_checks ("
                + " qc_id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + " manuscript_id BIGINT NOT NULL,"
                + " design_id BIGINT NOT NULL,"
                + " checked_by BIGINT NOT NULL,"
                + " result VARCHAR(20) NOT NULL,"
                + " checklist TEXT,"
                + " notes TEXT,"
                + " checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + " INDEX idx_qc_manuscript (manuscript_id),"
                + " CONSTRAINT fk_qc_manuscript FOREIGN KEY (manuscript_id)"
                + "   REFERENCES manuscripts(manuscript_id) ON UPDATE CASCADE ON DELETE CASCADE,"
                + " CONSTRAINT fk_qc_design FOREIGN KEY (design_id)"
                + "   REFERENCES book_designs(design_id) ON UPDATE CASCADE ON DELETE CASCADE"
                + ") ENGINE=InnoDB");

        // One published book per manuscript
        s.addUniqueIndex("books", "uq_books_manuscript", "manuscript_id");
    }
}

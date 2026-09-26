package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Epic 2: the production manager assigns a designer to an accepted manuscript (from the team's merged build). */
public class V201__Epic2_design_assignments extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        SchemaSteps s = new SchemaSteps(context.getConnection());
        s.requireBaseSchema("manuscripts", "users");
        s.exec("CREATE TABLE IF NOT EXISTS design_assignments ("
                + " assignment_id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + " manuscript_id BIGINT NOT NULL,"
                + " designer_id BIGINT NOT NULL,"
                + " assigned_by BIGINT NOT NULL,"
                + " assignment_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',"
                + " remarks VARCHAR(500),"
                + " assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + " completed_at TIMESTAMP NULL,"
                + " created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + " updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + " INDEX idx_design_assign_manuscript (manuscript_id),"
                + " INDEX idx_design_assign_designer (designer_id),"
                + " CONSTRAINT fk_design_assign_manuscript FOREIGN KEY (manuscript_id)"
                + "   REFERENCES manuscripts(manuscript_id) ON UPDATE CASCADE ON DELETE CASCADE"
                + ") ENGINE=InnoDB");
    }
}

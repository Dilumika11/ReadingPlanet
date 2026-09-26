package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Epic 4: the author signs their royalty agreement from the author portal ("Contracts & Royalties"). */
public class V401__Epic4_author_signature extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        SchemaSteps s = new SchemaSteps(context.getConnection());
        s.requireBaseSchema("royalty_agreements");
        s.addColumn("royalty_agreements", "author_signed_at", "TIMESTAMP NULL");
    }
}

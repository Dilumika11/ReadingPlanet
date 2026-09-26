package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Epic 3 (Publishing Operations) additions on top of docs/epms-schema.sql. */
public class V300__Epic3_publishing_operations extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        SchemaSteps s = new SchemaSteps(context.getConnection());
        s.requireBaseSchema("books", "print_orders", "inventory", "inventory_transactions", "customer_orders",
                "customer_order_items", "shipments", "bookstores", "bookstore_orders", "bookstore_order_items",
                "catalog_books");

        // Renames that docs/epms-schema.sql applies at the end; repeated here for older databases
        s.renameColumn("print_orders", "printer_name", "printing_company", "VARCHAR(150)");
        s.renameColumn("shipments", "courier_name", "shipping_provider", "VARCHAR(100)");
        s.addColumn("books", "cover_image", "VARCHAR(500) NULL");
        s.addColumn("customer_orders", "recipient_name", "VARCHAR(150) NOT NULL DEFAULT ''");
        s.addColumn("customer_orders", "recipient_phone", "VARCHAR(20) NULL");

        // Print orders: how much has been received into the warehouse (US23)
        s.addColumn("print_orders", "quantity_received", "INT NOT NULL DEFAULT 0");

        // Inventory: stock reserved by confirmed orders (US27) + optimistic lock
        s.addColumn("inventory", "quantity_reserved", "INT NOT NULL DEFAULT 0");
        s.addColumn("inventory", "version", "BIGINT NOT NULL DEFAULT 0");

        // Customer orders: cancellation and timeline (US27 - US29)
        s.addColumn("customer_orders", "cancel_reason", "VARCHAR(255) NULL");
        s.addColumn("customer_orders", "packed_at", "TIMESTAMP NULL");
        s.addColumn("customer_orders", "dispatched_at", "TIMESTAMP NULL");
        s.addColumn("customer_orders", "delivered_at", "TIMESTAMP NULL");

        // Wholesale orders: approval (US30) and fulfilment
        s.addColumn("bookstore_orders", "created_by", "BIGINT NULL");
        s.addColumn("bookstore_orders", "approved_by", "BIGINT NULL");
        s.addColumn("bookstore_orders", "approved_at", "TIMESTAMP NULL");
        s.addColumn("bookstore_orders", "rejection_reason", "VARCHAR(255) NULL");
        s.addColumn("bookstore_orders", "shipping_provider", "VARCHAR(100) NULL");
        s.addColumn("bookstore_orders", "tracking_number", "VARCHAR(100) NULL");
        s.addColumn("bookstore_orders", "dispatched_at", "TIMESTAMP NULL");
        s.addColumn("bookstore_orders", "delivered_at", "TIMESTAMP NULL");

        // Store listing -> the physical book that has stock and can be ordered
        s.addColumn("catalog_books", "stock_book_id", "BIGINT NULL");
        s.addIndex("catalog_books", "idx_catalog_books_stock", "stock_book_id");

        // Customer shopping carts (US26)
        s.exec("CREATE TABLE IF NOT EXISTS cart_items ("
                + " cart_item_id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + " user_id BIGINT NOT NULL,"
                + " catalog_book_id BIGINT NOT NULL,"
                + " quantity INT NOT NULL,"
                + " added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + " CONSTRAINT uq_cart_user_book UNIQUE (user_id, catalog_book_id),"
                + " CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES users(user_id)"
                + "   ON UPDATE CASCADE ON DELETE CASCADE,"
                + " CONSTRAINT fk_cart_book FOREIGN KEY (catalog_book_id) REFERENCES catalog_books(book_id)"
                + "   ON UPDATE CASCADE ON DELETE CASCADE"
                + ") ENGINE=InnoDB");
    }
}

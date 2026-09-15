CREATE DATABASE epms
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE epms;

CREATE TABLE roles (
    role_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_roles_role_name UNIQUE (role_name)
) ENGINE=InnoDB;

CREATE TABLE categories (
    category_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_categories_category_name UNIQUE (category_name)
) ENGINE=InnoDB;

CREATE TABLE genres (
    genre_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL,
    genre_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_genres_genre_name UNIQUE (genre_name),

    CONSTRAINT fk_genres_category
        FOREIGN KEY (category_id)
        REFERENCES categories(category_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE users (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,

    -- NOTE: password/full_name/role below are simplifications the current
    -- Shared Core auth code (com.epms.entity.User) actually persists to.
    -- The original design uses password_hash + first_name/last_name + the
    -- roles/user_roles tables instead. Kept here so `ddl-auto=validate`
    -- against a real MySQL schema matches the entity. Team should decide
    -- whether to migrate the entity to the proper user_roles design instead
    -- of carrying both.
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    role VARCHAR(30) NOT NULL,

    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,

    phone_number VARCHAR(20),

    profile_image VARCHAR(255),

    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    email_verified_at TIMESTAMP NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)

) ENGINE=InnoDB;

CREATE TABLE user_roles (
    user_role_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    user_id BIGINT NOT NULL,

    role_id BIGINT NOT NULL,

    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_user_role UNIQUE (user_id, role_id),

    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_id)
        REFERENCES roles(role_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE password_reset_tokens (
    token_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    user_id BIGINT NOT NULL,

    token VARCHAR(255) NOT NULL,

    expires_at TIMESTAMP NOT NULL,

    used_at TIMESTAMP NULL,

    is_used BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_password_reset_token UNIQUE (token),

    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE

) ENGINE=InnoDB;

CREATE TABLE user_sessions (
    session_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    user_id BIGINT NOT NULL,

    session_token VARCHAR(255) NOT NULL,

    ip_address VARCHAR(45) NOT NULL,

    user_agent VARCHAR(500),

    login_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    last_activity_at TIMESTAMP NULL,

    expires_at TIMESTAMP NOT NULL,

    logout_at TIMESTAMP NULL,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT uq_user_sessions_token UNIQUE (session_token),

    CONSTRAINT fk_user_sessions_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE

) ENGINE=InnoDB;

CREATE TABLE notifications (
    notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    user_id BIGINT NOT NULL,

    title VARCHAR(150) NOT NULL,

    message TEXT NOT NULL,

    notification_type VARCHAR(50) NOT NULL,

    related_entity_type VARCHAR(50),

    related_entity_id BIGINT,

    is_read BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE

) ENGINE=InnoDB;

CREATE TABLE audit_logs (
    audit_log_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    user_id BIGINT,

    action VARCHAR(100) NOT NULL,

    entity_name VARCHAR(100) NOT NULL,

    entity_id BIGINT,

    details TEXT,

    ip_address VARCHAR(45),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_logs_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL

) ENGINE=InnoDB;

-- Epic 1 – Author & Manuscript Management

CREATE TABLE authors (
    author_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    user_id BIGINT NOT NULL,

    author_code VARCHAR(20) NOT NULL,

    pen_name VARCHAR(150),

    biography TEXT,

    date_of_birth DATE,

    nationality VARCHAR(100),

    website VARCHAR(255),

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_authors_user UNIQUE (user_id),
    CONSTRAINT uq_authors_author_code UNIQUE (author_code),

    CONSTRAINT fk_authors_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE manuscripts (
    manuscript_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    author_id BIGINT NOT NULL,

    genre_id BIGINT NOT NULL,

    manuscript_code VARCHAR(20) NOT NULL,

    title VARCHAR(255) NOT NULL,

    synopsis TEXT,

    language VARCHAR(50) NOT NULL,

    word_count INT,

    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_manuscripts_code UNIQUE (manuscript_code),

    CONSTRAINT fk_manuscripts_author
        FOREIGN KEY (author_id)
        REFERENCES authors(author_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_manuscripts_genre
        FOREIGN KEY (genre_id)
        REFERENCES genres(genre_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE manuscript_files (
    manuscript_file_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    manuscript_id BIGINT NOT NULL,

    file_name VARCHAR(255) NOT NULL,

    file_path VARCHAR(500) NOT NULL,

    file_type VARCHAR(50) NOT NULL,

    file_size BIGINT,

    file_version INT NOT NULL,

    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_manuscript_version
        UNIQUE (manuscript_id, file_version),

    CONSTRAINT fk_manuscript_files_manuscript
        FOREIGN KEY (manuscript_id)
        REFERENCES manuscripts(manuscript_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE

) ENGINE=InnoDB;

CREATE TABLE manuscript_revisions (
    revision_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    manuscript_id BIGINT NOT NULL,

    reviewer_id BIGINT NOT NULL,

    revision_round INT NOT NULL,

    editor_comments TEXT NOT NULL,

    response_deadline DATE,

    responded_at TIMESTAMP NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_revision_round
        UNIQUE (manuscript_id, revision_round),

    CONSTRAINT fk_revision_manuscript
        FOREIGN KEY (manuscript_id)
        REFERENCES manuscripts(manuscript_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_revision_reviewer
        FOREIGN KEY (reviewer_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;

-- Epic 2 – Editorial Workflow & Book Production

CREATE TABLE editorial_reviews (
    review_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    manuscript_id BIGINT NOT NULL,

    reviewer_id BIGINT NOT NULL,

    review_round INT NOT NULL,

    review_comments TEXT NOT NULL,

    decision VARCHAR(30) NOT NULL,

    reviewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_editorial_review_round
        UNIQUE (manuscript_id, review_round),

    CONSTRAINT fk_editorial_reviews_manuscript
        FOREIGN KEY (manuscript_id)
        REFERENCES manuscripts(manuscript_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_editorial_reviews_reviewer
        FOREIGN KEY (reviewer_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE production_tasks (
    task_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    manuscript_id BIGINT NOT NULL,

    assigned_to BIGINT NOT NULL,

    assigned_by BIGINT NOT NULL,

    task_type VARCHAR(50) NOT NULL,

    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',

    task_description TEXT,

    due_date DATE,

    completed_at TIMESTAMP NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_production_tasks_manuscript
        FOREIGN KEY (manuscript_id)
        REFERENCES manuscripts(manuscript_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_production_tasks_assigned_to
        FOREIGN KEY (assigned_to)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_production_tasks_assigned_by
        FOREIGN KEY (assigned_by)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE book_designs (
    design_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    manuscript_id BIGINT NOT NULL,

    designer_id BIGINT NOT NULL,

    design_version INT NOT NULL,

    cover_file_path VARCHAR(500),

    layout_file_path VARCHAR(500),

    design_notes TEXT,

    design_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_book_design_version
        UNIQUE (manuscript_id, design_version),

    CONSTRAINT fk_book_designs_manuscript
        FOREIGN KEY (manuscript_id)
        REFERENCES manuscripts(manuscript_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_book_designs_designer
        FOREIGN KEY (designer_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE author_approvals (
    approval_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    design_id BIGINT NOT NULL,

    author_id BIGINT NOT NULL,

    approval_status VARCHAR(30) NOT NULL,

    comments TEXT,

    reviewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_author_approvals_design
        FOREIGN KEY (design_id)
        REFERENCES book_designs(design_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_author_approvals_author
        FOREIGN KEY (author_id)
        REFERENCES authors(author_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;

-- Epic 3 – Publishing Operations

CREATE TABLE books (
    book_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    manuscript_id BIGINT NOT NULL,

    category_id BIGINT NOT NULL,

    genre_id BIGINT NOT NULL,

    isbn VARCHAR(20) NOT NULL,

    title VARCHAR(255) NOT NULL,

    edition VARCHAR(50),

    publication_date DATE,

    price DECIMAL(12,2) NOT NULL,

    total_pages INT,

    language VARCHAR(50) NOT NULL,

    book_status VARCHAR(30) NOT NULL DEFAULT 'PUBLISHED',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_books_isbn UNIQUE (isbn),

    CONSTRAINT fk_books_manuscript
        FOREIGN KEY (manuscript_id)
        REFERENCES manuscripts(manuscript_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_books_category
        FOREIGN KEY (category_id)
        REFERENCES categories(category_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_books_genre
        FOREIGN KEY (genre_id)
        REFERENCES genres(genre_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE print_orders (
    print_order_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    book_id BIGINT NOT NULL,

    requested_by BIGINT NOT NULL,

    quantity INT NOT NULL,

    printer_name VARCHAR(150),

    order_date DATE NOT NULL,

    expected_completion_date DATE,

    completed_date DATE,

    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_print_orders_book
        FOREIGN KEY (book_id)
        REFERENCES books(book_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_print_orders_requested_by
        FOREIGN KEY (requested_by)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE inventory (
    inventory_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    book_id BIGINT NOT NULL,

    quantity_in_stock INT NOT NULL DEFAULT 0,

    reorder_level INT NOT NULL DEFAULT 10,

    warehouse_location VARCHAR(100),

    last_stock_update TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_inventory_book UNIQUE (book_id),

    CONSTRAINT fk_inventory_book
        FOREIGN KEY (book_id)
        REFERENCES books(book_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE inventory_transactions (
    transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    inventory_id BIGINT NOT NULL,

    performed_by BIGINT NOT NULL,

    transaction_type VARCHAR(30) NOT NULL,

    quantity INT NOT NULL,

    reference_type VARCHAR(50),

    reference_id BIGINT,

    remarks TEXT,

    transaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_inventory_transactions_inventory
        FOREIGN KEY (inventory_id)
        REFERENCES inventory(inventory_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_inventory_transactions_user
        FOREIGN KEY (performed_by)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE bookstores (
    bookstore_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    bookstore_name VARCHAR(150) NOT NULL,

    contact_person VARCHAR(150),

    email VARCHAR(100),

    phone_number VARCHAR(20),

    address VARCHAR(255),

    city VARCHAR(100),

    country VARCHAR(100),

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_bookstores_email UNIQUE (email)

) ENGINE=InnoDB;

CREATE TABLE customer_orders (
    customer_order_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    customer_id BIGINT NOT NULL,

    order_number VARCHAR(30) NOT NULL,

    order_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    total_amount DECIMAL(12,2) NOT NULL,

    order_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    shipping_address VARCHAR(255) NOT NULL,

    payment_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_customer_order_number UNIQUE (order_number),

    CONSTRAINT fk_customer_orders_customer
        FOREIGN KEY (customer_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE customer_order_items (
    customer_order_item_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    customer_order_id BIGINT NOT NULL,

    book_id BIGINT NOT NULL,

    quantity INT NOT NULL,

    unit_price DECIMAL(12,2) NOT NULL,

    subtotal DECIMAL(12,2) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_customer_order_items_order
        FOREIGN KEY (customer_order_id)
        REFERENCES customer_orders(customer_order_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_customer_order_items_book
        FOREIGN KEY (book_id)
        REFERENCES books(book_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE shipments (
    shipment_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    customer_order_id BIGINT NOT NULL,

    tracking_number VARCHAR(100),

    courier_name VARCHAR(100),

    shipped_date DATE,

    delivered_date DATE,

    shipment_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    shipping_cost DECIMAL(12,2),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_shipments_tracking UNIQUE (tracking_number),

    CONSTRAINT fk_shipments_customer_order
        FOREIGN KEY (customer_order_id)
        REFERENCES customer_orders(customer_order_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE bookstore_orders (
    bookstore_order_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    bookstore_id BIGINT NOT NULL,

    order_number VARCHAR(30) NOT NULL,

    order_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    total_amount DECIMAL(12,2) NOT NULL,

    order_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_bookstore_order_number UNIQUE (order_number),

    CONSTRAINT fk_bookstore_orders_bookstore
        FOREIGN KEY (bookstore_id)
        REFERENCES bookstores(bookstore_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE bookstore_order_items (
    bookstore_order_item_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    bookstore_order_id BIGINT NOT NULL,

    book_id BIGINT NOT NULL,

    quantity INT NOT NULL,

    unit_price DECIMAL(12,2) NOT NULL,

    subtotal DECIMAL(12,2) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_bookstore_order_items_order
        FOREIGN KEY (bookstore_order_id)
        REFERENCES bookstore_orders(bookstore_order_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_bookstore_order_items_book
        FOREIGN KEY (book_id)
        REFERENCES books(book_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;

--  Epic 4 – Administration, Finance & Royalty Management

CREATE TABLE payment_methods (
    payment_method_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    method_name VARCHAR(50) NOT NULL,

    description VARCHAR(255),

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_payment_methods_name UNIQUE (method_name)

) ENGINE=InnoDB;

CREATE TABLE invoices (
    invoice_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    customer_order_id BIGINT NOT NULL,

    invoice_number VARCHAR(30) NOT NULL,

    invoice_date DATE NOT NULL,

    subtotal DECIMAL(12,2) NOT NULL,

    tax_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,

    discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,

    total_amount DECIMAL(12,2) NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_invoice_number UNIQUE (invoice_number),

    CONSTRAINT fk_invoice_customer_order
        FOREIGN KEY (customer_order_id)
        REFERENCES customer_orders(customer_order_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE payments (
    payment_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    invoice_id BIGINT NOT NULL,

    payment_method_id BIGINT NOT NULL,

    payment_reference VARCHAR(100),

    amount_paid DECIMAL(12,2) NOT NULL,

    paid_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    payment_status VARCHAR(30) NOT NULL DEFAULT 'SUCCESS',

    processed_by BIGINT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payments_invoice
        FOREIGN KEY (invoice_id)
        REFERENCES invoices(invoice_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_payments_method
        FOREIGN KEY (payment_method_id)
        REFERENCES payment_methods(payment_method_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_payments_user
        FOREIGN KEY (processed_by)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL

) ENGINE=InnoDB;

CREATE TABLE royalty_agreements (
    royalty_agreement_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    author_id BIGINT NOT NULL,

    book_id BIGINT NOT NULL,

    agreement_number VARCHAR(30) NOT NULL,

    royalty_percentage DECIMAL(5,2) NOT NULL,

    effective_date DATE NOT NULL,

    expiry_date DATE,

    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_agreement_number UNIQUE (agreement_number),

    CONSTRAINT fk_royalty_author
        FOREIGN KEY (author_id)
        REFERENCES authors(author_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_royalty_book
        FOREIGN KEY (book_id)
        REFERENCES books(book_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;


CREATE TABLE royalty_payments (
    royalty_payment_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    royalty_agreement_id BIGINT NOT NULL,

    payment_reference VARCHAR(100) NOT NULL,

    amount DECIMAL(12,2) NOT NULL,

    payment_date DATE NOT NULL,

    payment_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    processed_by BIGINT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_royalty_payment_reference UNIQUE (payment_reference),

    CONSTRAINT fk_royalty_payment_agreement
        FOREIGN KEY (royalty_agreement_id)
        REFERENCES royalty_agreements(royalty_agreement_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_royalty_payment_user
        FOREIGN KEY (processed_by)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL

) ENGINE=InnoDB;

ALTER TABLE books
ADD COLUMN cover_image VARCHAR(500) NULL
AFTER edition;

ALTER TABLE print_orders
CHANGE printer_name printing_company VARCHAR(150);

ALTER TABLE shipments
CHANGE courier_name shipping_provider VARCHAR(100);

ALTER TABLE customer_orders
ADD COLUMN recipient_name VARCHAR(150) NOT NULL AFTER payment_status,
ADD COLUMN recipient_phone VARCHAR(20) AFTER recipient_name;

-- ============================================================
-- EPIC 4 ADDITIONS: Administration, Finance & Royalty Management
-- ============================================================
-- The `invoices` and `payments` tables defined earlier in this file
-- belong to Epic 3 (they FK directly to customer_order_id — customer
-- sales billing). Epic 4 needs a separate, polymorphic invoice/payment
-- model (royalty payouts, expense payments, general invoices keyed by
-- reference_type/reference_id) per the Epic 4 spec. To avoid a table
-- name collision they are added below as `financial_invoices` and
-- `financial_payments`. Confirm with the team whether this split is
-- correct or whether the two models should be merged.

CREATE TABLE royalty_calculations (
    calculation_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    royalty_agreement_id BIGINT NOT NULL,

    sales_period_start DATE NOT NULL,
    sales_period_end DATE NOT NULL,

    books_sold INT NOT NULL DEFAULT 0,
    gross_sales DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    deductions DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    royalty_base DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    royalty_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00,

    status VARCHAR(30) NOT NULL DEFAULT 'CALCULATED',

    calculated_by BIGINT,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    -- Enforces "same agreement + accounting period cannot be
    -- calculated/finalized twice" (prevents duplicate royalty payments).
    CONSTRAINT uq_royalty_calc_period
        UNIQUE (royalty_agreement_id, sales_period_start, sales_period_end),

    CONSTRAINT fk_royalty_calc_agreement
        FOREIGN KEY (royalty_agreement_id)
        REFERENCES royalty_agreements(royalty_agreement_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_royalty_calc_user
        FOREIGN KEY (calculated_by)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL

) ENGINE=InnoDB;

-- Link royalty_payments to the calculation it pays out, and add the
-- remaining columns from the Epic 4 RoyaltyPayment spec.
ALTER TABLE royalty_payments
ADD COLUMN calculation_id BIGINT NULL AFTER royalty_agreement_id,
ADD COLUMN payment_method VARCHAR(50) NULL AFTER amount,
ADD COLUMN approved_by BIGINT NULL AFTER payment_status,
ADD COLUMN approved_at TIMESTAMP NULL AFTER approved_by,
ADD COLUMN paid_at TIMESTAMP NULL AFTER approved_at,
ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP AFTER created_at,
ADD CONSTRAINT fk_royalty_payment_calculation
    FOREIGN KEY (calculation_id)
    REFERENCES royalty_calculations(calculation_id)
    ON UPDATE CASCADE
    ON DELETE RESTRICT,
ADD CONSTRAINT fk_royalty_payment_approver
    FOREIGN KEY (approved_by)
    REFERENCES users(user_id)
    ON UPDATE CASCADE
    ON DELETE SET NULL;

CREATE TABLE expenses (
    expense_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    category VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    amount DECIMAL(12,2) NOT NULL,
    expense_date DATE NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'RECORDED',

    approved_by BIGINT,
    created_by BIGINT NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_expenses_approver
        FOREIGN KEY (approved_by)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_expenses_creator
        FOREIGN KEY (created_by)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE financial_reports (
    report_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    report_type VARCHAR(50) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,

    generated_by BIGINT NOT NULL,
    generated_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    status VARCHAR(30) NOT NULL DEFAULT 'GENERATED',
    report_path VARCHAR(255),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_financial_reports_user
        FOREIGN KEY (generated_by)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;

CREATE TABLE financial_invoices (
    invoice_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    invoice_number VARCHAR(30) NOT NULL,
    reference_type VARCHAR(30) NOT NULL,
    reference_id BIGINT,

    amount DECIMAL(12,2) NOT NULL,
    tax_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(12,2) NOT NULL,

    invoice_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    file_path VARCHAR(255),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_financial_invoice_number UNIQUE (invoice_number)

) ENGINE=InnoDB;

CREATE TABLE financial_payments (
    payment_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    reference_number VARCHAR(100) NOT NULL,
    payment_type VARCHAR(30) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    payment_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payment_method VARCHAR(50),

    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    description VARCHAR(255),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_financial_payment_reference UNIQUE (reference_number)

) ENGINE=InnoDB;

CREATE TABLE system_settings (
    setting_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    setting_key VARCHAR(100) NOT NULL,
    setting_value VARCHAR(500),
    description VARCHAR(255),

    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_system_settings_key UNIQUE (setting_key),

    CONSTRAINT fk_system_settings_user
        FOREIGN KEY (updated_by)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL

) ENGINE=InnoDB;

CREATE TABLE announcements (
    announcement_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    published_at TIMESTAMP NULL,
    expires_at TIMESTAMP NULL,

    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_announcements_user
        FOREIGN KEY (created_by)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT

) ENGINE=InnoDB;



-- Inbound copy of Epic 3 sales data consumed by Epic 4 for revenue
-- monitoring (US38) and royalty calculation (US45). Epic 4 does not own
-- sales; only status = 'COMPLETED' rows count toward revenue/royalties.
-- Seeded with dummy data by the dev profile until Epic 3 exists.
CREATE TABLE sales_records (
    sale_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    sale_reference VARCHAR(40) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    book_id BIGINT NOT NULL,
    book_title VARCHAR(255) NOT NULL,

    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    sale_amount DECIMAL(12,2) NOT NULL,
    sale_date DATE NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',

    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_sales_records_reference UNIQUE (sale_reference),
    INDEX idx_sales_records_book_date (book_id, status, sale_date),
    INDEX idx_sales_records_date (sale_date)

) ENGINE=InnoDB;

-- Online-store catalogue managed from the admin panel (Epic 4). Kept apart
-- from Epic 2's books table (which is tied to manuscripts/production).
-- Text columns are utf8mb4 (database default) so Sinhala titles store as-is.
CREATE TABLE catalog_books (
    book_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    title VARCHAR(255) NOT NULL,
    author_name VARCHAR(150) NOT NULL,
    author_id BIGINT,

    category_id BIGINT,
    genre_id BIGINT,

    price DECIMAL(12,2) NOT NULL,
    isbn VARCHAR(20),
    description VARCHAR(2000),
    cover_image VARCHAR(255),
    new_arrival BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_catalog_books_category
        FOREIGN KEY (category_id)
        REFERENCES categories(category_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_catalog_books_genre
        FOREIGN KEY (genre_id)
        REFERENCES genres(genre_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL

) ENGINE=InnoDB;

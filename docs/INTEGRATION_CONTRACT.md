# EPMS Integration Contract (Epic 4 view)

Share this with the Epic 1, 2 and 3 owners. It lists what Epic 4 (Administration,
Finance & Royalty Management) needs from each epic, what Epic 4 provides to them,
and the rules that let us work in one repository without breaking each other.

## 1. Git workflow

- One repository, one Spring Boot app (`epms/`).
- `main` is always runnable; changes arrive by pull request.
- Work on feature branches named `epicN/<story>`, for example `epic4/us42-royalty-calc`.
- Only edit your own epic's files. Shared files (`pom.xml`, `application.properties`,
  `security/`, `enums/Role.java`, `contracts/`, `docs/epms-schema.sql`) change only by
  pull request with the team's agreement.
- Commit messages: `US42: implement royalty calculation service`.
- Never commit passwords. The datasource reads `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`
  environment variables (defaults are in `application.properties`).

## 2. Database changes (Flyway)

The base schema is `docs/epms-schema.sql`. Every later change is a Flyway migration in
`epms/src/main/resources/db/migration` (SQL) or `epms/src/main/java/db/migration` (Java).
Flyway runs before Hibernate validates the entities, so the app never starts on an
out-of-date schema. A database created before Flyway was added is baselined at version 1
automatically on the first start.

| Versions | Owner |
|---|---|
| V2 to V99 | Shared core (users, roles) |
| V100 to V199 | Epic 1 |
| V200 to V299 | Epic 2 |
| V300 to V399 | Epic 3 |
| V400 to V499 | Epic 4 |

Rules: never edit a migration that has been merged; add a new one. Stay inside your
range. Make migrations safe to run on a database that already has the change (the Epic 4
migration `V400__Epic4_finance_and_royalty.java` checks each column and index first).

## 3. Roles

One `Role` enum (`com.epms.enums.Role`) for everyone. Use these exact names:

`ADMIN`, `AUTHOR`, `EDITOR`, `PROOFREADER`, `DESIGNER`, `PRODUCTION_MANAGER`,
`INVENTORY_STAFF`, `SALES_STAFF`, `FINANCE_STAFF`, `EXECUTIVE`, `CUSTOMER`.

`EXECUTIVE` was added for the read-only executive dashboards.

## 4. What Epic 4 reads from other epics (the ports)

Epic 4 never imports another epic's classes or queries its tables directly from business
code. It goes through two interfaces in `com.epms.contracts`, plus the sales seam:

```java
public interface AuthorDirectory {             // Epic 1
    Optional<AuthorDto> findAuthor(Long authorId);
    Optional<AuthorDto> findByUserId(Long userId);
    List<AuthorDto> findAll();
}
public interface BookCatalog {                 // Epics 2 / 3
    Optional<BookDto> findBook(Long bookId);
    List<BookDto> findByAuthor(Long authorId);
    List<BookDto> findAll();
}
public interface SalesDataService {            // Epic 3 (com.epms.service)
    List<SalesRecord> getCompletedSaleLines(Long bookId, LocalDate from, LocalDate to);
    List<SalesRecord> getReturnedSaleLines(Long bookId, LocalDate from, LocalDate to);
    ...
}
```

Current adapters (`com.epms.adapter`) read these tables read-only:

| Port | Reads | Fields Epic 4 relies on |
|---|---|---|
| AuthorDirectory | `authors` joined to `users` | `author_id`, `user_id`, `status`, `pen_name` or `users.full_name`, `users.email` |
| BookCatalog | `books` joined to `manuscripts` | `book_id`, `title`, `isbn`, `manuscripts.author_id`, `category_id`, `genre_id`, `price` (list price), `publication_date`, `book_status` |
| SalesDataService | `sales_records` (inbound copy of Epic 3 sales) | `sale_reference`, `channel`, `book_id`, `book_title`, `quantity`, `unit_price`, `sale_amount`, `discount`, `sale_date`, `status` |

If you rename or restructure one of these, tell the Epic 4 owner: only the adapter changes,
not the Epic 4 services.

### Checklist for Epic 1 (authors)

- [ ] Every author has a row in `authors` linked to a `users` row (`user_id`), and that
      user has role `AUTHOR`. This is how an author's login finds their royalties.
- [ ] Keep `author_id`, `user_id` and `status` as they are.
- [ ] Royalty terms live in Epic 4's `royalty_agreements` (rate, basis, wholesale rate,
      advance, frequency, dates). If Epic 1 keeps signed contracts, tell us so we can
      store the contract id on the agreement.

### Checklist for Epics 2 / 3 (books)

- [ ] A published book has a row in `books` with `price` (the list price) and a
      `manuscript_id` whose manuscript has the right `author_id`.
- [ ] `category_id` and `genre_id` come from Epic 4's categories and genres (see section 5).

### Checklist for Epic 3 (sales)

- [ ] For every order line, provide: order reference, channel (`CUSTOMER` retail or
      `BOOKSTORE` wholesale), book id and title, quantity, unit price, line amount
      (quantity x unit price), discount, sale date and status.
- [ ] Mark a line `COMPLETED` only after delivery / fulfilment. Only completed lines count
      toward revenue and royalties.
- [ ] Use `CANCELLED` for cancelled lines and `RETURNED` for returned ones. They are shown
      as excluded and never earn royalty.
- [ ] Agree with Epic 4 whether you write these rows into `sales_records` or Epic 4 reads
      your order tables through a new `SalesDataService` implementation.

## 5. What Epic 4 provides to other epics

No login is needed for these reads:

| Endpoint | Returns |
|---|---|
| `GET /api/categories` | All categories (`categoryId`, `categoryName`, `description`) |
| `GET /api/genres` | All genres (`genreId`, `genreName`, `description`) |
| `GET /api/settings/public` | `currency`, `taxRatePercent`, `invoicePrefix`, `companyName`, `companyAddress` |

All responses use the shared envelope `{ "success", "message", "data" }`.
Changes to categories, genres and settings are ADMIN only.

## 6. Adding your existing code

1. Create a branch `epicN/<story>` from `main`.
2. Copy your classes into the shared packages (`controller`, `service`, `entity`, ...),
   keeping to your own tables. Do not change Epic 4's classes.
3. Move your SQL into a Flyway migration in your version range. Do not edit
   `docs/epms-schema.sql` directly without telling the team.
4. Protect your endpoints in `SecurityConfig` with your roles (by pull request).
5. Run `bash mvnw test`, start the app against MySQL, and open a pull request.

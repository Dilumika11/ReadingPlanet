# Reading Planet — Enterprise Publishing Management System (EPMS)

University group project (SLIIT, ISPM 2026). Digitizes a book publishing house's core
operations: manuscript submission, editorial workflow, book production, printing,
inventory, customer/wholesale orders, and — this module — administration, finance and
royalty management.

## Structure

```
epms/    Spring Boot backend (Java 21, Spring Boot 3.5.6, MySQL + Flyway, JWT auth)
docs/    Project documentation (epic spec, sprint 0 report, DB schema, status reports)
```

## Running locally

Needs Java 21 and MySQL 8.

```bash
mysql -u root -p < docs/epms-schema.sql     # base schema
cd epms
bash mvnw spring-boot:run                   # Flyway applies each epic's migrations
mysql -u root -p epms < ../docs/demo-data.sql   # optional demo data (after the first start)
```

> Use `bash mvnw`, not `./mvnw`: the wrapper script carries a macOS quarantine flag.

Credentials default to `root` / `12345`; override with `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`.

Then open:
- Staff and author login: http://localhost:8080/admin-login.html
- Swagger UI: http://localhost:8080/swagger-ui/index.html

Docs:
- `TESTING.md`: demo accounts, demo script, walkthrough, automated tests
- `progress.md`: Epic 4 status by user story, open decisions
- `docs/API.md`: Epic 4 endpoints and roles
- `docs/INTEGRATION_CONTRACT.md`: what Epic 4 needs from and gives to the other epics

## Architecture

Four epics share one authentication/authorization core ("Shared Core" — users, JWT,
roles/RBAC):

- **Epic 1** — Author & Manuscript Management
- **Epic 2** — Editorial Workflow & Book Production
- **Epic 3** — Publishing Operations (printing, inventory, orders, delivery)
- **Epic 4** — Administration, Finance & Royalty Management (this module's active focus)

See `docs/epic-4-spec.pdf` for the full domain spec, ownership boundaries, and API
contracts for Epic 4.

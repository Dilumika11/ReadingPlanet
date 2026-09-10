# Reading Planet — Enterprise Publishing Management System (EPMS)

University group project (SLIIT, ISPM 2026). Digitizes a book publishing house's core
operations: manuscript submission, editorial workflow, book production, printing,
inventory, customer/wholesale orders, and — this module — administration, finance and
royalty management.

## Structure

```
epms/    Spring Boot backend (Java 21, Spring Boot 3.5.6, MySQL, JWT auth)
docs/    Project documentation (epic spec, sprint 0 report, DB schema, status reports)
```

## Running locally (no MySQL setup required)

The backend has a `dev` profile that uses an in-memory H2 database, so you don't need
MySQL installed to try it out:

```bash
cd epms
bash mvnw -q -Dspring-boot.run.profiles=dev spring-boot:run
```

> Use `bash mvnw`, not `./mvnw` — the wrapper script carries a macOS quarantine flag
> that blocks direct execution.

Then open:
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Staff login page: http://localhost:8080/admin-login.html

See `TESTING.md` for a runnable demo script and a walkthrough mapping each
demoed behavior back to a Sprint 0 user story. See `progress.md` for what's
done vs. still open on Epic 4.

## Running against real MySQL

1. Create the database using `docs/epms-schema.sql`.
2. Update `epms/src/main/resources/application.properties` with your MySQL
   credentials (or override via environment variables).
3. Run without the `dev` profile:
   ```bash
   cd epms
   bash mvnw spring-boot:run
   ```

## Architecture

Four epics share one authentication/authorization core ("Shared Core" — users, JWT,
roles/RBAC):

- **Epic 1** — Author & Manuscript Management
- **Epic 2** — Editorial Workflow & Book Production
- **Epic 3** — Publishing Operations (printing, inventory, orders, delivery)
- **Epic 4** — Administration, Finance & Royalty Management (this module's active focus)

See `docs/epic-4-spec.pdf` for the full domain spec, ownership boundaries, and API
contracts for Epic 4.

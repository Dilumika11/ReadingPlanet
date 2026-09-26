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

Optional settings, all from environment variables (nothing secret is kept in the repo):

| Feature | Variables |
|---|---|
| Password reset e-mails | `SPRING_MAIL_HOST`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` (without them the e-mail is written to the log) |
| Google sign-in | `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID`, `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET` (the Google buttons stay hidden without them) |
| Links in e-mails | `APP_BASE_URL` (default `http://localhost:8080`) |

Then open:
- Online store: http://localhost:8080/ (store, cart, checkout, account at `/account`)
- Getting Published (apply as a new author): http://localhost:8080/getting-published
- Author portal: http://localhost:8080/author-login.html
- Staff dashboard (every staff role): http://localhost:8080/admin-login.html
- Swagger UI: http://localhost:8080/swagger-ui/index.html

`bash scripts/demo-lifecycle.sh` (in `epms/`, with the app running) walks one book from
submission to a delivered order through every role; `bash scripts/demo.sh` checks Epic 4.

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
- **Epic 4** — Administration, Finance & Royalty Management

All four epics are implemented in this codebase. The public store, cart, account and
author portal pages come from the team's merged Epic 1/2 build; the author pages reach
the EPMS API through `js/author-api.js`, and the staff screens for Epics 1 to 3 are in
`js/epic123.js`.

See `docs/epic-4-spec.pdf` for the full domain spec, ownership boundaries, and API
contracts for Epic 4.

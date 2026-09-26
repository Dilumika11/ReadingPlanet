# Demoing & Testing Epic 4

## 1. Set up the database (MySQL 8)

```bash
mysql -u root -p < docs/epms-schema.sql          # base schema, creates database "epms"
cd epms
bash mvnw spring-boot:run                        # Flyway applies the Epic 4 changes (V400)
# in a second terminal, once "Started EpmsApplication" appears:
mysql -u root -p epms < docs/demo-data.sql       # demo accounts, books, ~300 sales, agreements
```

Database credentials default to `root` / `12345` on `localhost:3306/epms`; override with
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD` environment variables.

> Use `bash mvnw`, not `./mvnw`: the wrapper carries a macOS quarantine flag.

## 2. Demo accounts (password `Password@123` for all)

| Email | Role | Sees |
|---|---|---|
| admin@readingplanet.lk | ADMIN | Categories, genres, settings, announcements, books; finance read-only |
| finance@readingplanet.lk | FINANCE_STAFF | Revenue, expenses, invoices, payments, reports, royalties |
| exec@readingplanet.lk | EXECUTIVE | Executive analytics; finance read-only |
| author1@readingplanet.lk ... author6@ | AUTHOR | Their own "My Royalties" page |

Log in at `http://localhost:8080/admin-login.html` (authors land on their own page).

## 3. Run the scripted demo

```bash
bash epms/scripts/demo.sh        # BASE_URL=http://host:port to point elsewhere
```

It walks through every story with PASS/FAIL lines: administration, revenue, agreement
validation, preview and calculation with the duplicate / overlap / future-period guards,
statement, approval, payment (wrong amount, reuse and double payment rejected), author
isolation (403 on another author's statement), expenses, invoices and payments, report
finalization, and analytics. It is safe to re-run.

## 4. A good live walkthrough in the browser

1. **Finance**: Royalty Calculations, pick `RA-DEMO-003` (has a 25,000 advance), choose last
   quarter, **Preview** (every sale line with its rate), then **Save**. Calculate the quarter
   before it and watch the advance being recouped across both.
2. Open a calculation, **Issue Statement**, then **Statement** to see the printable version.
3. **Statement Approvals**: approve. **Royalty Payments**: record the payment. Try the same
   bank reference twice.
4. **Invoices**: create, issue, record a part payment, print.
5. **Financial Reports**: generate a profit and loss, finalize it, see that it can no longer change.
6. Log in as **exec** for the analytics dashboards, and as **author1** for "My Royalties".

## 5. Automated tests

```bash
cd epms
bash mvnw test
```

65 tests, run on an in-memory H2 database (`src/test/resources/application-test.properties`),
never on MySQL:

- `RoyaltyCalculatorTest`: net sales vs list price basis, wholesale rate, returns, advance
  partly then fully recouped across two periods, carried-forward amounts, deductions.
- `RoyaltyServiceImplTest`: overlapping period rejected, period outside the agreement or in
  the future rejected, no sales rejected, preview saves nothing, cancel frees the period and
  gives back the advance, statement numbering, approval vs carry forward, author ownership,
  agreement validation and overlapping activation.
- `RoyaltyPaymentServiceImplTest`: duplicate payment reference, amount must equal payable,
  cannot pay without approval or twice, ROYALTY expense posted.
- `FinanceServiceImplTest`: invoice numbering and tax, only drafts editable, payment above
  balance, duplicate payment reference, status follows payments, expense rules.
- `ReportingServiceImplTest`: finalized report immutability, CSV export.
- `DocumentNumberServiceTest`: `INV-2026-00001` style numbers per year.
- `RoleAccessIntegrationTest` (full Spring context, real JWTs): author cannot read another
  author's statement, executive cannot write finance data, finance cannot change settings,
  reference data is public.
- `SalesDataServiceImplTest`, `BookServiceImplTest`: existing tests, still passing.

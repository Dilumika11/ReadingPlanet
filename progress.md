# Epic 4 Progress: Administration, Finance & Royalty Management

Story IDs follow the User Stories table (US31 to US50). The Sprint 0 backlog used shifted
numbers for Epic 4 (for example its US34 categories is US31 here, its US45 royalty
calculation is US42).

## Status

| Story | What | Status |
|---|---|---|
| US31 | Manage categories | Done. Unique name (any case), max 100 chars; delete refused while a book uses the category; public read |
| US32 | Manage genres | Done. Same rules; public read |
| US33 | System settings | Done. Validated finance keys (currency, tax rate 0-100, invoice prefix, royalty payment threshold, company name/address) with defaults; every change audited (who, when, old, new); invoices store the tax rate and currency they were created with; `GET /api/settings/public` |
| US34 | Announcements | Done. Audience (ALL or a role), show-from / show-until window, draft; `GET /api/announcements/active` |
| US35 | Monitor revenue | Done. Completed sales only, net of discounts; filters for date range, channel, book, category; monthly totals |
| US36 | Expenses | Done. Fixed categories, receipt upload (PDF/JPG/PNG, 5 MB), RECORDED -> REVIEWED -> APPROVED -> POSTED or REJECTED with reason; only pending can be edited or deleted |
| US37 | Invoices | Done. `INV-2026-00042` numbers from a locked counter, never reused; line items, tax, total; DRAFT/ISSUED/PARTIALLY_PAID/PAID/CANCELLED; only drafts editable; printable page |
| US38 | Record payments | Done. Against an issued invoice, cannot exceed the balance, unique reference, status updates automatically |
| US39 | Financial reports | Done. REVENUE, EXPENSE, PROFIT_AND_LOSS; figures stored as a snapshot; CSV download |
| US40 | Finalize reports | Done. GENERATED -> REVIEWED -> FINALIZED; finalized reports reject every change with 409; finalized by/at recorded |
| US41 | Royalty agreements | Done. Basis (net sales / list price), wholesale rate, advance, payment frequency; author and book checked through the ports, book must belong to the author; no overlapping ACTIVE agreements for a book |
| US42 | Calculate royalties | Done. Line by line from completed sales; wholesale rate; returns listed, no royalty; advance recoupment carried across periods; every line stored; preview before saving |
| US43 | Prevent duplicate calculation | Done. Any overlapping live period rejected (service) plus a DB unique key; cancelling a CALCULATED record (with reason) frees the period |
| US44 | Royalty statements | Done. `RS-2026-00001`, full breakdown, printable page |
| US45 | Approve royalty payments | Done. Approve or reject (back to CALCULATED or cancel); below the threshold is carried forward into the next calculation |
| US46 | Record royalty payments | Done. Only approved; amount must equal payable; unique reference (service + DB); PAID is immutable; posted as a ROYALTY expense |
| US47 | Author views own royalties | Done. `/api/me/royalty/*` resolves the author from the login; another author's statement is 403; author portal page |
| US48 | Revenue and sales analytics | Done. KPIs, revenue by month, channel split, top categories (Chart.js) |
| US49 | Book and author performance | Done. Top 10 books by units and revenue, top authors, trend per book |
| US50 | Royalty analytics | Done. Paid vs outstanding, royalty as % of revenue, liability per author |

Cross-cutting:

- Role-based access for every Epic 4 endpoint (see `docs/API.md`); executives are read-only.
- Optimistic locking (`@Version`) on agreements, calculations, royalty payments and invoices,
  so two people approving or paying the same record at once get a 409, not a double payment.
- Audit entries in the shared `audit_logs` table for settings changes and every royalty,
  invoice and report workflow step.
- Flyway manages schema changes (Epic 4 owns V400 to V499), applied on top of
  `docs/epms-schema.sql`.

## Integration with the other epics

Epic 4 reads other epics only through `AuthorDirectory`, `BookCatalog` (package
`com.epms.contracts`) and `SalesDataService`. The current adapters read the shared schema's
`authors`, `books` / `manuscripts` and `sales_records` tables; if those tables are empty or
missing the screens still load and show nothing. `docs/demo-data.sql` fills them for demos.
See `docs/INTEGRATION_CONTRACT.md` for what each teammate needs to provide.

## Known gaps and decisions to confirm with the team

- Categories and genres are deleted (refused while in use) rather than archived: an earlier
  team change replaced archiving with plain CRUD, and that was kept.
- Authentication stays JWT (shared core), not session login. `POST /api/auth/register`
  still lets anyone create an account with any role, including ADMIN; the shared core
  owner should restrict it.
- Returned sales: Epic 3 marks a returned sale `RETURNED` instead of sending a separate
  negative line, so a returned sale simply never earns royalty (and is listed on the
  statement). Confirm this with Epic 3.
- No Epic 1 contract table exists yet, so agreements do not store a contract id.
- Paid royalties are posted as ROYALTY expenses; the profit and loss report counts them
  once (as royalties paid), not twice.
- Inventory statistics belong to Epic 3 (`/api/analytics/inventory` returns 501).
- Chart.js and Font Awesome load from cdnjs; without internet access charts and icons are
  missing, but tables and buttons still work.

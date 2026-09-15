# Epic 4 Progress — Administration, Finance & Royalty Management

Tracking against `docs/epic-4-spec.pdf` (the authoritative domain spec) and the Epic 4
backlog in `docs/sprint-0-report.docx`. This pass is a full recheck against the actual
codebase — endpoint-by-endpoint against spec sections 29-34, and state-machine-by-state-
machine against sections 8-10 — not just a summary of what was recently added.

## Sprint 1 Preview (the 5 stories committed for this sprint)

| Story | Description | Status |
|---|---|---|
| US34 | Manage book categories | ✅ Done — create/edit/archive, duplicate-name rejected (409) |
| US36 | Configure system settings | ✅ Done — get all + upsert by key |
| US38 | Monitor revenue from completed sales | ✅ Done — `GET /api/finance/revenue` (total, monthly, by channel, top books, excluded cancelled/returned) + Revenue screen and dashboard cards; runs on dummy Epic 3 sales data |
| US44 | Access royalty agreements/terms | ✅ Done — full CRUD + DRAFT→ACTIVE→EXPIRED lifecycle |
| US45 | Calculate royalties from completed sales | ✅ Done — engine pulls books sold / gross sales from the COMPLETED sales for the agreement's book and period (no longer typed in by the caller); rejects a period with no completed sales; unit-tested |

**Sprint 1: 5 of 5 stories done.** Both US38 and US45 run against **dummy Epic 3 sales data**
seeded by the dev profile, because Epic 3 has not been built. See "Epic 3 stand-in" below for
how that is wired and what changes when the real feed exists.

## Epic 3 stand-in (dummy sales data)

Epic 3 (Sales & Distribution) does not exist yet, so Epic 4 keeps its own inbound copy of the
data spec section 52 says it *receives* from Epic 3: table `sales_records` / entity
`SalesRecord` (order reference, channel CUSTOMER|BOOKSTORE, book id + title snapshot, quantity,
unit price, amount, sale date, status COMPLETED|CANCELLED|RETURNED). Everything that needs sales
goes through one interface, `SalesDataService`:

- `getRevenue(from, to)` → US38
- `getCompletedSalesForBook(bookId, from, to)` → US45
- `getSales(from, to, status)` → raw feed for finance to audit

`DemoSalesDataInitializer` (only when `epms.demo-data.enabled=true`, i.e. the dev profile, and
only when the table is empty) seeds ~290 deterministic sales over the last 12 months for a
6-book dummy catalogue, ~8% CANCELLED and ~4% RETURNED so exclusion is visible, plus 6 ACTIVE
agreements `RA-DEMO-001..006`. Nothing runs against MySQL unless that flag is set.

When Epic 3 lands: replace `SalesDataServiceImpl` with one that reads Epic 3's tables/API
(and confirm with Epic 3's owner which `order_status` means "completed"). Revenue, royalty and
the frontend don't change.

Also added this pass: `@Transactional` on `RoyaltyServiceImpl`, `RoyaltyPaymentServiceImpl`,
`FinanceServiceImpl`, `ReportingServiceImpl` (spec §45), and the first unit tests
(`RoyaltyServiceImplTest`, `SalesDataServiceImplTest` — 11 tests, all green).

## What's built (verified working — API tested via curl/demo script, UI tested via real browser DOM)

- **Category / Genre** — full CRUD + archive, unique-name enforced
- **System Settings** — list + upsert by key
- **Announcements** — create/update/archive
- **Admin dashboard** — real aggregate counts (categories/genres/announcements)
- **Royalty agreements** — full CRUD, DRAFT → ACTIVE → EXPIRED lifecycle, rejects a second ACTIVE agreement for the same author+book
- **Revenue monitoring (US38)** — `GET /api/finance/revenue?from&to` (defaults to the last 12 calendar months): total revenue, completed-sales count, books sold, average sale, revenue by channel, month-by-month trend (empty months included), top 5 books, and the cancelled/returned transactions excluded. `GET /api/finance/sales` exposes the raw feed; `GET /api/finance/sales/summary?bookId&from&to` gives the per-book totals a royalty will be based on.
- **Royalty calculation (first step only — see gap below)** — validates the agreement is ACTIVE and covers the sales period, rejects a duplicate calculation for the same agreement+period (service check + DB unique constraint backstop), **pulls books sold / gross sales from the COMPLETED sales for the agreement's book** (rejects a period with none), computes `royaltyBase = grossSales - deductions` and `royaltyAmount = royaltyBase × rate` with `BigDecimal`. Lands the record in `CALCULATED` status.
- **Royalty payment status transitions (only — see gap below)** — approve → schedule → process → mark-paid, rejects out-of-order transitions, rejects marking paid without approval
- **Expense record + approve (partial — see gap below)** — create, edit-while-RECORDED, approve; edit blocked once approved
- **Financial report finalize (partial — see gap below)** — rejects modifying an already-finalized report
- **Frontend** — Admin/Finance Dashboard (now with revenue + royalty cards), **Revenue** (date filter, monthly table, top books, channels, exclusions), Categories, Genres, Settings, Announcements, Royalty Agreements, Royalty Calculations (previews the completed sales found before you calculate), Expenses all have real screens wired to the above, reusing the shared dashboard shell
- DB schema for all 11 Epic-4-owned tables + the `sales_records` inbound table (`docs/epms-schema.sql`)
- All 34 API endpoints listed in spec sections 29-34 exist and respond (a mix of fully working and honest `UnsupportedOperationException` stubs — see below)

## What's left

### 1. Connect Royalty Calculation → Royalty Payment (biggest gap found this recheck)
There is currently **no way to create a `RoyaltyPayment` row at all.** `RoyaltyPaymentService`
only has `approve/schedule/process/markPaid` — all of them state *transitions*, none of them
an origin. The spec's workflow (`Generate Statement → Finance Review → Approve Payment →
Schedule → Process → Mark Paid`) assumes a payment already exists in `PENDING` once a
calculation is finalized, but nothing produces that row. Practically: you can calculate a
royalty, but there is no path from that calculation to an actual payment you could approve.
**This should be the next thing built** — e.g. a `POST /api/royalties/{id}/generate-payment`
(or equivalent) that creates a `PENDING` `RoyaltyPayment` from a calculation, generating its
unique `payment_reference`.

### 2. Royalty Calculation's own review/approval sub-workflow is missing
Spec section 8 defines two *separate* state machines:
- `RoyaltyCalculation`: `SALES_RECEIVED → CALCULATED → REVIEWED → APPROVED → STATEMENT_GENERATED`
- `RoyaltyPayment`: `PENDING → APPROVED → SCHEDULED → PROCESSING → PAID`

Only the payment one has transition logic (see "What's built"). The calculation one only has
its first step (`calculate()` → `CALCULATED`) — there's no `review()`, no `approve()` on the
calculation itself, and no `STATEMENT_GENERATED` transition. `GET /api/royalties/{id}/statement`
just returns the raw row regardless of status, with no check that it's actually ready to be a
statement. This sub-workflow and item 1 are two faces of the same missing piece: nothing moves
a calculation from "computed" to "finance has signed off, pay it."

### 3. Expense workflow is incomplete
Spec: `RECORDED → REVIEWED → APPROVED → POSTED`, and "rejected expenses should be explicitly
recorded rather than deleted." Built: `RECORDED → APPROVED` only (skips `REVIEWED` and
`POSTED` entirely) and **there is no reject endpoint** — an expense can only be approved,
never explicitly rejected with a reason. The `Expense` entity also has no field to record a
rejection reason if that gets added.

### 4. No concurrency protection (transactions now done)
Spec's non-functional requirements (section 45):
- ~~*"Royalty calculation and payment state changes must be transactional"*~~ — **done**:
  `@Transactional` is on all four state-changing service impls; the duplicate-calculation
  backstop uses `saveAndFlush` so the unique-constraint violation is caught inside the
  transaction.
- *"Two finance officers must not be able to approve/process the same royalty payment
  simultaneously"* — still open: no optimistic locking (`@Version`) or pessimistic locking. The
  read-check-write sequence in e.g. `RoyaltyPaymentServiceImpl.approve()` (read status, check
  it's PENDING, then save) has a real race window: two concurrent requests could both pass the
  status check before either commits. Fix alongside item 1, since it's the same code.

### 5. Royalty statement generation & author-facing view (US47 — not started)
- No real statement formatting (see item 2 — it returns the raw calculation)
- No endpoint for an author to fetch **their own** statements/payment history, and no
  ownership/IDOR check ("author cannot view another author's statement" — spec section 42 —
  is currently unenforced; any authenticated user can query `GET /api/royalties/author/{id}`
  for any author id)

### 6. Finance module: invoicing, report generation (US37, US35/39)
- ~~`getRevenue()`~~ — **done** (US38, on dummy sales data — see "Epic 3 stand-in")
- `generateInvoice()` — stub, no logic for producing a `financial_invoices` row from a
  royalty/expense/sale reference
- `ReportingService.generate()` — stub; can now aggregate sales (via `SalesDataService`) +
  expenses + royalty data for the period (spec section 36 sequence). Its `REVIEWED`
  intermediate status is also skipped — `finalize()` jumps straight from whatever status to
  `FINALIZED`.
- No endpoint to **record a generic financial payment** (`FinancialPayment`) — only reads exist

### 7. Analytics module (US48, US49, US50)
All 7 `/api/analytics/*` endpoints are stubs. Sales/revenue analytics can now be built on
`SalesDataService` (same dummy data); book and author performance still need Epic 1 (author)
and Epic 2 (book) data that doesn't exist yet.

### 8. Authorization (cuts across everything)
Every Epic 4 endpoint only requires *a* valid JWT — there is still no role enforcement. No
`@PreAuthorize`, no `@EnableMethodSecurity`, no `hasRole(...)` anywhere in the codebase. Needs
wiring to the permissions matrix in spec section 11 (e.g. only FINANCE_STAFF can approve
royalty payments; ADMIN-only for categories/genres/settings; EXECUTIVE is read-only; AUTHOR is
scoped to own records). This is arguably the single highest-value remaining item: it's
self-contained (doesn't depend on Epic 3), and it's the difference between a demo and
something that actually enforces the spec's core security model.

### 9. Audit integration
Spec requires Epic 4 to emit events (royalty calculated, payment approved, report finalized,
etc.) to the Shared Core audit service. Nothing wired yet — depends on Shared Core exposing an
audit-log API/service to call into. Confirmed: no `AuditLog`/`AuditService` reference anywhere
in this codebase.

### 10. Frontend — partially done
Built (verified end-to-end in a real browser): Admin/Finance Dashboard, Categories, Genres,
Settings, Announcements, Royalty Agreements (create/activate/expire), Royalty Calculations
(create + list), Expenses (create/approve).

Still missing: Royalty Statements view (blocked on item 2/5), Royalty Payments screen
(approve/schedule/process/mark-paid — backend exists but nothing can originate a payment yet,
see item 1), Invoices, Financial Reports, Analytics Dashboard (spec section 37).

### 11. Tests
First unit tests are in: `RoyaltyServiceImplTest` (7) and `SalesDataServiceImplTest` (4) cover
the US45/US38 rules — rate applied to completed sales, duplicate prevention, DRAFT agreement
rejected, cancelled/returned excluded, etc. Still not automated from the spec section 55
checklist: payment-without-approval rejection, finalized-report immutability, author IDOR,
concurrent-approval rejection. Those remain manual (demo script / browser).

### 12. Open cross-team decisions
- `financial_invoices`/`financial_payments` were named to avoid colliding with Epic 3's
  existing `invoices`/`payments` tables — confirm this split with the team rather than
  assuming it
- Confirm the Epic 1 contract ↔ Epic 4 royalty-terms ownership question (spec section 15) —
  current implementation assumes Epic 4 owns `RoyaltyAgreement` outright
- No Epic 3 API contract exists yet for "completed sales" — Epic 4 currently runs on its own
  `sales_records` stand-in with dummy data. Agree with Epic 3's owner: which order status means
  "completed", whether Epic 4 reads their tables directly or gets a feed, and how
  returns/cancellations after the fact are signalled

## Suggested order of attack

1. **Connect calculation → payment** (item 1) + the calculation's own review/approve/statement
   sub-workflow (item 2) — these unblock the rest of Royalty and are self-contained
2. **Add optimistic locking** (item 4) while touching that same code
3. **Role-based authorization** (item 8) — self-contained, highest security value, doesn't
   depend on Epic 3
4. Author-facing statement endpoint + IDOR check (item 5)
5. Finish the Expense workflow: REVIEWED/POSTED + reject-with-reason (item 3)
6. Build invoice generation, report generation and sales analytics against `SalesDataService`
   (the dummy data is enough to demo them); nail down the real Epic 3 contract in parallel
7. Book/author analytics (last — depends on Epic 1/2 having real data)
8. Frontend screens for Royalty Payments/Statements/Invoices/Reports as each backend piece lands
9. Tests — ideally alongside each item above, not deferred to the end; at minimum, backfill
   the spec section 55 checklist once items 1-5 land

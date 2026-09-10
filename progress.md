# Epic 4 Progress — Administration, Finance & Royalty Management

Tracking against `docs/epic-4-spec.pdf` and the Epic 4 backlog in
`docs/sprint-0-report.docx`. Last updated after the initial scaffolding pass.

## Sprint 1 Preview (the 5 stories committed for this sprint)

| Story | Description | Status |
|---|---|---|
| US34 | Manage book categories | ✅ Done — create/edit/archive, duplicate-name rejected (409) |
| US36 | Configure system settings | ✅ Done — get all + upsert by key |
| US38 | Monitor revenue from completed sales | ❌ Not started — blocked on Epic 3 completed-sales API |
| US44 | Access royalty agreements/terms | ✅ Done — full CRUD + DRAFT→ACTIVE→EXPIRED lifecycle |
| US45 | Calculate royalties from completed sales | ✅ Done (interim) — engine works, tested; takes sales figures as direct input until Epic 3's completed-sales API exists |

**Sprint 1: 4 of 5 stories done.** Only US38 (revenue monitoring) remains, and it's blocked on the same missing Epic 3 dependency as the interim part of US45.

## What's built (working, tested end-to-end)

- **Category** — full CRUD + archive, unique-name enforced
- **Genre** — full CRUD + archive, unique-name enforced
- **System Settings** — list + upsert by key
- **Announcements** — create/update/archive
- **Admin dashboard** — real aggregate counts (categories/genres/announcements)
- **Royalty agreements** — full CRUD, DRAFT → ACTIVE → EXPIRED lifecycle, rejects activating a second ACTIVE agreement for the same author+book
- **Royalty calculation engine** — validates the agreement is ACTIVE and covers the sales period, rejects a duplicate calculation for the same agreement+period (service-level check + DB unique constraint as a concurrency backstop), computes `royaltyBase = grossSales - deductions` and `royaltyAmount = royaltyBase × rate` with `BigDecimal`
- **Royalty payment state machine** — approve → schedule → process → mark-paid, rejects out-of-order transitions, rejects marking paid without approval
- **Financial report finalize** — rejects modifying an already-finalized report
- **Expense create/update/approve** — edit blocked once past RECORDED status
- DB schema for all 11 Epic-4-owned tables (`docs/epms-schema.sql`), including a unique constraint that blocks a duplicate royalty calculation for the same agreement + period at the database level

## What's left

### 1. Royalty Agreement management — ✅ done
~~No `RoyaltyAgreementController`...~~ Built: CRUD + DRAFT→ACTIVE→EXPIRED + one-active-per-author/book guard.
Still open: decide with the team whether Epic 4 is the authoritative owner of royalty financial terms, or reads a reference from Epic 1's contract (spec section 15) — current implementation assumes Epic 4 owns them.

### 2. Royalty calculation engine — ✅ done (interim)
Built: active-agreement validation, period-coverage validation, duplicate-period rejection (service check + DB constraint backstop), `BigDecimal` math.
Still open:
- Swap the request's direct `booksSold`/`grossSales`/`deductions` input for a real Epic 3 completed-sales lookup once that API exists
- `recalculate()` — needs authorization check + audit trail per spec rule 8 (currently stubbed)

### 3. Royalty statement generation (US44 author-facing part)
- `GET /api/royalties/{id}/statement` currently just returns the raw calculation row — needs an actual statement view/format
- No endpoint for an author to fetch **their own** statements/payment history — **US47 not started at all**, including the IDOR check ("author cannot view another author's statement")

### 4. Finance module (US38, US37, US35/39)
- `getRevenue()` — stub, blocked on Epic 3 completed-sales data
- `generateInvoice()` — stub, no logic for producing a `financial_invoices` row from a royalty/expense/sale reference
- Financial report `generate()` — stub; needs to actually aggregate sales + expenses + royalty data for the period (spec section 36 sequence)
- No endpoint to **record a generic financial payment** (`FinancialPayment`) — only read endpoints exist right now

### 5. Analytics module (US48, US49, US50)
- All 7 endpoints (`/api/analytics/*`) are stubs — every one needs Epic 1 (author), Epic 2 (book), and/or Epic 3 (sales) data that doesn't exist yet
- This is realistically the last thing to build, once the upstream epics have something to query

### 6. Authorization (cuts across everything)
- Right now every Epic 4 endpoint only requires *a* valid JWT — there's no role enforcement yet
- Need `@PreAuthorize`/method security wired to the permissions matrix in spec section 11 (e.g. only FINANCE_STAFF can approve royalty payments; ADMIN-only for categories/genres/settings; EXECUTIVE is read-only; AUTHOR is scoped to own records)

### 7. Audit integration
- Spec requires Epic 4 to emit events (royalty calculated, payment approved, report finalized, etc.) to the Shared Core audit service
- Nothing wired yet — depends on Shared Core exposing an audit-log API/service to call into

### 8. Frontend
- Only the shared admin-login/dashboard shell exists; it currently calls `/api/production` and `/api/warehouse` (someone else's epics), not any Epic 4 endpoint
- No screens built yet for: Royalty Dashboard, Royalty Agreements, Royalty Calculations, Royalty Statements, Royalty Payments, Finance Dashboard, Expenses, Invoices, Financial Reports, Analytics Dashboard (spec section 37)

### 9. Tests
- No unit/integration/API tests written yet for anything above (spec section 55 has the checklist: duplicate-calculation prevention, payment-without-approval rejection, finalized-report immutability, author IDOR, etc.)

### 10. Open cross-team decisions
- `financial_invoices`/`financial_payments` were named to avoid colliding with Epic 3's existing `invoices`/`payments` tables — confirm this split with the team rather than assuming it
- Confirm the Epic 1 contract ↔ Epic 4 royalty-terms ownership question (item 1 above) before building agreement CRUD, since it affects the data model
- No Epic 3 API contract exists yet for "completed sales" — this blocks items 2, 3 (revenue) and half of Analytics; worth raising with Epic 3's owner as a priority dependency

## Suggested order of attack

1. ~~`RoyaltyAgreementController` + CRUD~~ ✅ done
2. ~~Royalty calculation engine~~ ✅ done (interim, direct sales input)
3. Nail down the Epic 3 completed-sales contract (even a stub/mock response shape) so the calculation engine and revenue can be wired to real data instead of direct input
4. Author-facing royalty statement endpoint (with ownership check) + real statement formatting
5. Finance: expense workflow completion, invoice generation, financial report generation
6. Role-based authorization (`@PreAuthorize`) across all of the above
7. Analytics (last — depends on everything upstream having real data)
8. Frontend screens per module as each backend piece lands
9. Tests alongside each module, not deferred to the end

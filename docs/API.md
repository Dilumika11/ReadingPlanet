# Epic 4 API

Base path `/api`. JSON in and out, wrapped as `{ "success": true|false, "message": "...", "data": ... }`.
Authenticate with `Authorization: Bearer <token>` from `POST /api/auth/login`.
Full, try-it-out documentation: Swagger UI at `/swagger-ui/index.html`.

Errors: 400 invalid input (validation errors also return `data.fieldErrors`), 401 not logged in,
403 role not allowed, 404 not found, 409 business rule conflict (duplicate period, finalized
report, wrong status, ...), 501 not available yet.

Roles: **A** = ADMIN, **F** = FINANCE_STAFF, **E** = EXECUTIVE, **Au** = AUTHOR, **Pub** = no login.
Finance and royalty reads (GET) are open to F, A and E; every change needs F.

## Administration (US31 - US34)

| Method | Path | Roles | Notes |
|---|---|---|---|
| GET | `/categories`, `/categories/{id}` | Pub | |
| POST, PUT, DELETE | `/categories`, `/categories/{id}` | A | Unique name (any case); delete refused while a book uses it |
| GET | `/genres`, `/genres/{id}` | Pub | |
| POST, PUT, DELETE | `/genres`, `/genres/{id}` | A | |
| GET | `/settings` | A | All settings |
| PUT | `/settings` | A | `{settingKey, settingValue, description}`; validates `currency`, `taxRatePercent` (0-100), `invoicePrefix`, `royaltyPaymentThreshold`, `companyName`, `companyAddress`; audited |
| GET | `/settings/history` | A | Who changed what, old and new value |
| GET | `/settings/public` | Pub | Currency, tax rate, invoice prefix, company |
| GET, POST, PUT, DELETE | `/announcements`... | A | `{title, content, audience (ALL or role), publishFrom, publishTo, draft}` |
| GET | `/announcements/active` | any login | Visible now to the caller's role |
| GET | `/admin/dashboard` | A, F, E | Counters |
| GET | `/me` | any login | Current user, role, `authorId` for authors |

## Finance (US35 - US40)

| Method | Path | Notes |
|---|---|---|
| GET | `/finance/revenue?from&to&channel&bookId&categoryId` | Net revenue from COMPLETED sales (sale amount less discount); monthly, by channel, top books, excluded |
| GET | `/finance/sales?from&to&status`, `/finance/sales/summary?bookId&from&to` | Raw sales feed from Epic 3 |
| GET | `/finance/expenses?status`, `/finance/expenses/{id}`, `/finance/expenses/categories` | |
| POST | `/finance/expenses` | `{category, amount, expenseDate, description}`; category one of PRINTING, SALARIES, MARKETING, RENT, UTILITIES, ROYALTY, OTHER |
| PUT, DELETE | `/finance/expenses/{id}` | Only RECORDED (pending) |
| POST | `/finance/expenses/{id}/review`, `/approve`, `/post` | RECORDED -> REVIEWED -> APPROVED -> POSTED |
| POST | `/finance/expenses/{id}/reject` | `{reason}`; kept as REJECTED |
| POST, GET | `/finance/expenses/{id}/receipt` | multipart `file`: PDF, JPG or PNG, max 5 MB |
| GET | `/invoices?status`, `/invoices/{id}` | Detail includes lines and payments |
| POST | `/invoices` | `{customerName, customerEmail, customerAddress, invoiceDate, dueDate, notes, lines:[{description, quantity, unitPrice}]}`; DRAFT, number `INV-2026-00042`, tax rate copied from settings |
| PUT | `/invoices/{id}` | DRAFT only |
| POST | `/invoices/{id}/issue` | DRAFT -> ISSUED |
| POST | `/invoices/{id}/cancel` | `{reason}`; DRAFT, or ISSUED with no payments |
| POST | `/invoices/generate?referenceType=SALE&referenceId=` | Draft invoice from a completed sale |
| POST | `/invoices/{id}/payments` | `{amount, paymentDate, paymentMethod (CASH, BANK_TRANSFER, CARD, CHEQUE), reference}`; not above the balance; unique reference; sets PARTIALLY_PAID / PAID |
| GET | `/payments`, `/payments/{id}` | |
| GET | `/reports`, `/reports/{id}` | Detail includes the stored figures |
| POST | `/reports/generate?reportType&periodStart&periodEnd` | REVENUE, EXPENSE or PROFIT_AND_LOSS; figures stored as a snapshot |
| POST | `/reports/{id}/regenerate`, `/review`, `/finalize` | FINALIZED is read-only (409 on any change) |
| DELETE | `/reports/{id}` | Not when finalized |
| GET | `/reports/{id}/download` | CSV |

## Royalties (US41 - US47)

| Method | Path | Notes |
|---|---|---|
| GET | `/royalty-agreements`, `/royalty-agreements/{id}`, `/royalty-agreements/author/{authorId}` | |
| GET | `/royalty-agreements/lookup/authors`, `/lookup/books?authorId` | From the Epic 1/2 ports |
| POST | `/royalty-agreements` | `{authorId, bookId, royaltyPercentage (0.01-50), wholesaleRoyaltyPercentage, basis (NET_SALES, LIST_PRICE), advanceAmount, paymentFrequency (QUARTERLY, BIANNUAL, ANNUAL), effectiveDate, expiryDate}`; author and book must exist and match; DRAFT |
| PUT | `/royalty-agreements/{id}` | DRAFT only |
| POST | `/royalty-agreements/{id}/activate`, `/expire` | Activation refused if another ACTIVE agreement for the book overlaps |
| GET | `/royalties?status`, `/royalties/{id}` | Detail includes every sale line |
| POST | `/royalties/preview` | `{royaltyAgreementId, periodStart, periodEnd, deductions}`; calculates, saves nothing |
| POST | `/royalties/calculate` | Same body; 409 if a live calculation for the agreement overlaps the period |
| POST | `/royalties/{id}/cancel` | `{reason}`; CALCULATED only; frees the period, returns the recouped advance |
| POST | `/royalties/{id}/recalculate` | CALCULATED only |
| POST | `/royalties/{id}/statement` | CALCULATED -> STATEMENT_ISSUED, number `RS-2026-00001` |
| GET | `/royalties/{id}/statement` | Statement data |
| POST | `/royalties/{id}/approve` | STATEMENT_ISSUED -> APPROVED (creates the payment) or CARRIED_FORWARD below the threshold |
| POST | `/royalties/{id}/reject` | `{reason, cancel}` -> CALCULATED or CANCELLED |
| POST | `/royalties/{id}/pay` | `{transactionReference (unique), paymentDate, paymentMethod, amount (= payable)}` -> PAID; posts a ROYALTY expense |
| GET | `/royalty-payments`, `/royalty-payments/{id}` | |
| POST | `/royalty-payments/{id}/schedule`, `/process`, `/mark-paid` | Optional steps; `mark-paid` takes the same body as `/pay` |
| GET | `/me/royalty/agreements`, `/me/royalty/statements`, `/me/royalty/statements/{id}`, `/me/royalty/payments` | Au only; own data, 403 for another author's statement |

Calculation, per completed sale line: base = NET_SALES `sale amount - discount` or LIST_PRICE
`quantity x list price`; rate = wholesale rate for BOOKSTORE sales when set, else the standard rate;
royalty = base x rate / 100. Returned sales earn nothing and are listed on the statement.
Payable = gross royalty - remaining advance (never below 0) + amounts carried forward.

## Analytics (US48 - US50), roles E, A, F

All take optional `from` and `to` (default: last 12 months).

| Path | Returns |
|---|---|
| `/analytics/dashboard` | KPIs (revenue this month, year to date, orders, units), revenue by month, channel split, top categories |
| `/analytics/revenue` | Revenue summary |
| `/analytics/sales` | Orders, units and revenue by channel and by month |
| `/analytics/books` | Top 10 books by units and by revenue |
| `/analytics/books/{bookId}/trend` | Monthly units and revenue for one book |
| `/analytics/authors` | Top authors |
| `/analytics/royalties` | Paid vs outstanding, royalty as % of revenue, liability by author |
| `/analytics/inventory` | 501: owned by Epic 3 |

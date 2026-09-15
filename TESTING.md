# Demoing & Testing Epic 4

How to show what's built so far and prove it works — for your own testing, or
for a supervisor/evaluator session. Everything here runs locally with no
MySQL setup (in-memory H2, reset on every restart).

## 1. Start the app

```bash
cd epms
bash mvnw -q -Dspring-boot.run.profiles=dev spring-boot:run
```

> Use `bash mvnw`, not `./mvnw` — the wrapper script carries a macOS
> quarantine flag that blocks direct execution.

Wait for `Started EpmsApplication` in the log, then it's live at
`http://localhost:8080`.

On startup the dev profile also seeds **dummy Epic 3 sales data** — ~290
sales across the last 12 months for a 6-book dummy catalogue (some
CANCELLED / RETURNED so exclusion can be shown) plus 6 ACTIVE royalty
agreements (`RA-DEMO-001`..`006`). This is what revenue monitoring and
royalty calculation run against until Epic 3 exists. Look for
`Demo sales data: seeded ...` in the log. It is controlled by
`epms.demo-data.enabled=true` in `application-dev.properties` and only
runs when the sales table is empty, so it never touches real data.

## 2. Fastest way to show it working: run the demo script

```bash
bash epms/scripts/demo.sh
```

This registers a Finance Officer and an Admin, then walks through every
implemented feature with labeled PASS/FAIL output — category/genre CRUD,
duplicate-name rejection, the full royalty agreement lifecycle, the royalty
calculation engine pulling its figures from the seeded completed sales
(including the duplicate-calculation and no-active-agreement business
rules), expense approval + immutability, revenue monitoring, and finally
shows the still-TODO analytics endpoint failing with a clear message instead
of crashing. Safe to re-run repeatedly against the same server — it
randomizes names/ids per run so nothing collides with a previous pass.

Good as a live demo: run it in front of an evaluator and narrate each
`== section ==` as it prints.

## 3. Interactive exploration: Swagger UI

`http://localhost:8080/swagger-ui/index.html` — every endpoint, with
request/response schemas, directly testable in the browser. Click
**Authorize**, paste a JWT from `/api/auth/login`, and every subsequent
"Try it out" call carries it.

## 4. Staff frontend shell

`http://localhost:8080/admin-login.html` — log in with a registered
account (run the demo script first, then use
`demo_finance@readingplanet.test` / `Passw0rd!` for the Finance view or
`demo_admin@readingplanet.test` / `Passw0rd!` for the Admin view).

Good things to show in the browser:

- **Finance Dashboard** — revenue / completed sales / books sold cards built
  from the seeded sales.
- **Revenue** — monthly breakdown, top books, revenue by channel, and the
  cancelled/returned transactions that were *excluded* (US38). Change the
  date range and hit Apply.
- **Royalty Calculations** — pick a `RA-DEMO-*` agreement and a period; the
  form previews the completed sales it found ("N sales, N books sold, gross
  Rs X") before you click Calculate. Submit the same period twice to show the
  duplicate being rejected (US45).

## 5. What each demoed behavior proves, mapped to Sprint 0 stories

| Demo step | User story | What it proves |
|---|---|---|
| Create/duplicate/archive category | US31 | Categories can be created, edited (archived), duplicates rejected |
| Create genre | US32 | Genre management works |
| Upsert setting | US33 | System settings configurable |
| Create announcement | US34 | Announcement management works |
| Create agreement (DRAFT) | US44 | Royalty agreements/terms can be accessed & created |
| Calculate against DRAFT → 409 | US45, business rule | "No active royalty agreement" is enforced, not just documented |
| Activate agreement | US44 | Agreement lifecycle (DRAFT→ACTIVE) works |
| Completed sales for book/period | US45, rule 1 | The figures a royalty is based on come from Epic 3's completed sales, not typed in |
| Calculate royalty = gross × rate (verified exact) | US45, US42 | Correct royalty rate applied to the completed-sales gross, `BigDecimal` precision (not floating point) |
| Duplicate calculation → 409 | US43 | **Duplicate royalty calculation prevention** — the single most emphasized integrity rule in the spec |
| Retrieve statement | US44 | Royalty statement retrieval works |
| Record/approve expense, edit-after-approve → 409 | US36 (backlog), financial rule 18 | Expenses trackable; approved records are immutable |
| Revenue (12 months / filtered / inverted period → 409) | US38, rule 12 | Revenue monitoring from completed sales only; cancelled + returned counted separately as excluded |
| Raw sales feed (`status=CANCELLED`) | US38 | Finance can inspect exactly what was received from Epic 3 and why it was excluded |
| `/api/analytics/dashboard` → 500 with message | US48-50 | Same — analytics honestly blocked on upstream epics |

## 6. Manual spot-checks (if you want to show a specific thing by hand)

```bash
# Register + login
curl -s -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d \
  '{"username":"you","firstName":"You","lastName":"Name","email":"you@test.com","password":"Passw0rd!","role":"FINANCE_STAFF"}'

TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"you@test.com","password":"Passw0rd!"}' | python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# Revenue for the last 12 months (or add ?from=YYYY-MM-DD&to=YYYY-MM-DD)
curl -s http://localhost:8080/api/finance/revenue -H "Authorization: Bearer $TOKEN"

# What Epic 4 received from Epic 3 for one book in a period
curl -s "http://localhost:8080/api/finance/sales/summary?bookId=1&from=2026-08-01&to=2026-08-31" -H "Authorization: Bearer $TOKEN"

# Calculate a royalty — no sales figures in the body, they come from the completed sales
curl -s -X POST http://localhost:8080/api/royalties/calculate -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"royaltyAgreementId":1,"periodStart":"2026-08-01","periodEnd":"2026-08-31","deductions":0}'
```

## 7. Automated tests

```bash
cd epms
bash mvnw test
```

`RoyaltyServiceImplTest` covers the US45 rules (rate applied to completed
sales, deductions, DRAFT agreement rejected, duplicate rejected, period
outside agreement, no completed sales, deductions > gross) and
`SalesDataServiceImplTest` covers the US38 aggregation (only COMPLETED
counts, cancelled/returned reported as excluded, monthly/channel/top-book
breakdown).

## Notes for whoever is evaluating

- The dev H2 database is a file at `~/.readingplanet/epms-dev.mv.db`, and
  uploaded book covers live in `~/.readingplanet/uploads/covers/` — both
  **survive restarts**, so books/covers/accounts you add in the admin panel
  stay. Delete the `~/.readingplanet` folder to start completely fresh (the
  demo data is re-seeded on the next start).
- `progress.md` at the project root has the full breakdown of what's done
  vs. still open, with a suggested build order.
- Anything returning HTTP 500 with a message starting "not yet
  implemented" is a deliberate, documented gap (see `progress.md`), not a
  bug — each one names the spec section and what it's blocked on.
- The sales data is **dummy** — Epic 3 hasn't been built. It lives in
  Epic 4's `sales_records` table (the inbound copy of what spec section 52
  says Epic 4 receives) behind a `SalesDataService` interface, so swapping
  in the real Epic 3 feed later is a one-class change and nothing in
  revenue or royalty code has to move.

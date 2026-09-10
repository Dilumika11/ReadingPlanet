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

## 2. Fastest way to show it working: run the demo script

```bash
bash epms/scripts/demo.sh
```

This registers a Finance Officer and an Admin, then walks through every
implemented feature with labeled PASS/FAIL output — category/genre CRUD,
duplicate-name rejection, the full royalty agreement lifecycle, the royalty
calculation engine (including the duplicate-calculation and
no-active-agreement business rules), expense approval + immutability, and
finally shows the still-TODO endpoints failing with a clear message instead
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
account. Note: the dashboard's Production/Warehouse widgets call other
epics' endpoints (not built), so they'll show errors — expected. No
Epic-4-specific screens exist yet (see `progress.md` item 8).

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
| Calculate royalty = exactly 10,000.00 | US45, US42 | Correct royalty rate applied, `BigDecimal` precision (not floating point) |
| Duplicate calculation → 409 | US43 | **Duplicate royalty calculation prevention** — the single most emphasized integrity rule in the spec |
| Retrieve statement | US44 | Royalty statement retrieval works |
| Record/approve expense, edit-after-approve → 409 | US36 (backlog), financial rule 18 | Expenses trackable; approved records are immutable |
| `/api/finance/revenue` → 500 with message | US38 | Honestly shows this is blocked on Epic 3, not silently faked |
| `/api/analytics/dashboard` → 500 with message | US48-50 | Same — analytics honestly blocked on upstream epics |

## 6. Manual spot-checks (if you want to show a specific thing by hand)

```bash
# Register + login
curl -s -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d \
  '{"username":"you","firstName":"You","lastName":"Name","email":"you@test.com","password":"Passw0rd!","role":"FINANCE_STAFF"}'

TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"you@test.com","password":"Passw0rd!"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# Anything else, e.g.:
curl -s http://localhost:8080/api/royalty-agreements -H "Authorization: Bearer $TOKEN"
```

## Notes for whoever is evaluating

- The H2 database is in-memory and **resets every time the app restarts** —
  don't expect data to persist between demo sessions unless you keep the
  same process running.
- `progress.md` at the project root has the full breakdown of what's done
  vs. still open, with a suggested build order.
- Anything returning HTTP 500 with a message starting "not yet
  implemented" is a deliberate, documented gap (see `progress.md`), not a
  bug — each one names the spec section and what it's blocked on.

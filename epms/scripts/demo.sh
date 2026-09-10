#!/usr/bin/env bash
#
# End-to-end demo/test script for Epic 4 (Administration, Finance & Royalty
# Management). Run this against a live instance of the app to walk through
# every implemented feature with visible pass/fail output.
#
# Usage:
#   cd epms && bash mvnw -q -Dspring-boot.run.profiles=dev spring-boot:run &
#   bash scripts/demo.sh
#
set -uo pipefail

BASE="${BASE_URL:-http://localhost:8080}"
PASS=0
FAIL=0

step() { printf "\n\033[1;34m== %s ==\033[0m\n" "$1"; }
ok()   { PASS=$((PASS+1)); printf "  \033[1;32mPASS\033[0m %s\n" "$1"; }
bad()  { FAIL=$((FAIL+1)); printf "  \033[1;31mFAIL\033[0m %s (got: %s)\n" "$1" "$2"; }

expect_code() {
  local desc="$1" method="$2" url="$3" data="${4:-}" auth="${5:-}"
  local code
  if [ -n "$data" ]; then
    code=$(curl -s -o /tmp/demo_last_body -w "%{http_code}" -X "$method" "$BASE$url" \
      -H "Content-Type: application/json" ${auth:+-H "$auth"} -d "$data")
  else
    code=$(curl -s -o /tmp/demo_last_body -w "%{http_code}" -X "$method" "$BASE$url" ${auth:+-H "$auth"})
  fi
  echo "$code"
}

# parse_float=str preserves "10000.00" instead of collapsing it to the
# float 10000.0, so decimal amounts print exactly as the API returned them.
json() { python3 -c "import sys,json; d=json.load(sys.stdin, parse_float=str); print(d$1)"; }

# Use fresh, randomized names/ids each run so the script is safely
# re-runnable against the same long-lived dev server without tripping
# uniqueness constraints or the "one active agreement per author+book"
# rule from a previous run's leftover data.
RUN_ID=$RANDOM
DEMO_AUTHOR_ID=$((RANDOM + 1000))
DEMO_BOOK_ID=$((RANDOM + 1000))

# ---------------------------------------------------------------------------
step "0. Register + login a Finance Officer and an Admin"
# ---------------------------------------------------------------------------

curl -s -X POST "$BASE/api/auth/register" -H "Content-Type: application/json" -d '{
  "username":"demo_finance","firstName":"Demo","lastName":"Finance",
  "email":"demo_finance@readingplanet.test","password":"Passw0rd!","role":"FINANCE_STAFF"
}' > /dev/null

curl -s -X POST "$BASE/api/auth/register" -H "Content-Type: application/json" -d '{
  "username":"demo_admin","firstName":"Demo","lastName":"Admin",
  "email":"demo_admin@readingplanet.test","password":"Passw0rd!","role":"ADMIN"
}' > /dev/null

FINANCE_TOKEN=$(curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  -d '{"email":"demo_finance@readingplanet.test","password":"Passw0rd!"}' | json "['data']['token']")
ADMIN_TOKEN=$(curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  -d '{"email":"demo_admin@readingplanet.test","password":"Passw0rd!"}' | json "['data']['token']")

if [ -n "$FINANCE_TOKEN" ] && [ -n "$ADMIN_TOKEN" ]; then
  ok "Register + login (JWT issued for both roles) — Shared Core auth, not this epic's scope, but required to test it"
else
  bad "Register/login" "no token returned"
  echo "Aborting — cannot continue without auth."
  exit 1
fi

FIN="Authorization: Bearer $FINANCE_TOKEN"
ADM="Authorization: Bearer $ADMIN_TOKEN"

# ---------------------------------------------------------------------------
step "1. Administration — Categories (US31)"
# ---------------------------------------------------------------------------

CODE=$(expect_code "create category" POST "/api/categories" "{\"categoryName\":\"Demo Fiction $RUN_ID\",\"description\":\"Created by demo script\"}" "$ADM")
[ "$CODE" = "200" ] && ok "Create category -> 200" || bad "Create category" "$CODE"
CATEGORY_ID=$(cat /tmp/demo_last_body | json "['data']['categoryId']")

CODE=$(expect_code "duplicate category name rejected" POST "/api/categories" "{\"categoryName\":\"Demo Fiction $RUN_ID\"}" "$ADM")
[ "$CODE" = "409" ] && ok "Duplicate category name -> 409 Conflict" || bad "Duplicate category rejection" "$CODE"

CODE=$(expect_code "archive category" PATCH "/api/categories/$CATEGORY_ID/archive" '' "$ADM")
[ "$CODE" = "200" ] && ok "Archive category -> 200" || bad "Archive category" "$CODE"

# ---------------------------------------------------------------------------
step "2. Administration — Genres (US32), Settings (US33), Announcements (US34)"
# ---------------------------------------------------------------------------

CODE=$(expect_code "create genre" POST "/api/genres" "{\"genreName\":\"Demo Fantasy $RUN_ID\"}" "$ADM")
[ "$CODE" = "200" ] && ok "Create genre -> 200" || bad "Create genre" "$CODE"

CODE=$(expect_code "update setting" PUT "/api/settings" '{"settingKey":"currency","settingValue":"LKR"}' "$ADM")
[ "$CODE" = "200" ] && ok "Upsert system setting -> 200" || bad "Upsert setting" "$CODE"

CODE=$(expect_code "create announcement" POST "/api/announcements" '{"title":"Demo","content":"Sprint 0 demo run"}' "$ADM")
[ "$CODE" = "200" ] && ok "Create announcement -> 200" || bad "Create announcement" "$CODE"

CODE=$(expect_code "admin dashboard" GET "/api/admin/dashboard" '' "$ADM")
[ "$CODE" = "200" ] && ok "Admin dashboard aggregate stats -> 200" || bad "Admin dashboard" "$CODE"

# ---------------------------------------------------------------------------
step "3. Royalty Agreements (US44) — full CRUD + lifecycle"
# ---------------------------------------------------------------------------

CODE=$(expect_code "create agreement" POST "/api/royalty-agreements" \
  "{\"authorId\":$DEMO_AUTHOR_ID,\"bookId\":$DEMO_BOOK_ID,\"royaltyPercentage\":10,\"effectiveDate\":\"2026-01-01\"}" "$FIN")
[ "$CODE" = "200" ] && ok "Create royalty agreement (DRAFT) -> 200" || bad "Create agreement" "$CODE"
AGREEMENT_ID=$(cat /tmp/demo_last_body | json "['data']['royaltyAgreementId']")

CALC_BODY="{\"royaltyAgreementId\":$AGREEMENT_ID,\"periodStart\":\"2026-07-01\",\"periodEnd\":\"2026-07-31\",\"booksSold\":100,\"grossSales\":100000,\"deductions\":0}"

CODE=$(expect_code "calculate before activation is rejected" POST "/api/royalties/calculate" "$CALC_BODY" "$FIN")
[ "$CODE" = "409" ] && ok "Calculate against a DRAFT agreement -> 409 (no active agreement)" || bad "Reject calc on DRAFT" "$CODE"

CODE=$(expect_code "activate agreement" POST "/api/royalty-agreements/$AGREEMENT_ID/activate" '' "$FIN")
[ "$CODE" = "200" ] && ok "Activate royalty agreement -> 200" || bad "Activate agreement" "$CODE"

# ---------------------------------------------------------------------------
step "4. Royalty Calculation Engine (US45, US42, US43)"
# ---------------------------------------------------------------------------

CODE=$(expect_code "calculate royalty" POST "/api/royalties/calculate" "$CALC_BODY" "$FIN")
AMOUNT=$(cat /tmp/demo_last_body | json "['data']['royaltyAmount']")
if [ "$CODE" = "200" ] && [ "$AMOUNT" = "10000.00" ]; then
  ok "Calculate royalty: 100,000 gross x 10% = 10,000.00 (BigDecimal, verified exact)"
else
  bad "Royalty calculation" "code=$CODE amount=$AMOUNT"
fi
CALCULATION_ID=$(cat /tmp/demo_last_body | json "['data']['calculationId']")

CODE=$(expect_code "duplicate calculation rejected" POST "/api/royalties/calculate" "$CALC_BODY" "$FIN")
[ "$CODE" = "409" ] && ok "Duplicate calculation for same agreement+period -> 409 (prevents double royalty payment)" \
  || bad "Duplicate calculation rejection" "$CODE"

CODE=$(expect_code "royalty statement" GET "/api/royalties/$CALCULATION_ID/statement" '' "$FIN")
[ "$CODE" = "200" ] && ok "Retrieve royalty statement -> 200" || bad "Royalty statement" "$CODE"

# ---------------------------------------------------------------------------
step "5. Expenses (part of Finance module)"
# ---------------------------------------------------------------------------

CODE=$(expect_code "record expense" POST "/api/finance/expenses" \
  '{"category":"Printing","description":"Demo expense","amount":5000,"expenseDate":"2026-07-15"}' "$FIN")
[ "$CODE" = "200" ] && ok "Record expense -> 200" || bad "Record expense" "$CODE"
EXPENSE_ID=$(cat /tmp/demo_last_body | json "['data']['expenseId']")

CODE=$(expect_code "approve expense" POST "/api/finance/expenses/$EXPENSE_ID/approve" '' "$FIN")
[ "$CODE" = "200" ] && ok "Approve expense -> 200" || bad "Approve expense" "$CODE"

CODE=$(expect_code "edit approved expense rejected" PUT "/api/finance/expenses/$EXPENSE_ID" \
  '{"category":"Printing","description":"edited","amount":6000,"expenseDate":"2026-07-15"}' "$FIN")
[ "$CODE" = "409" ] && ok "Edit an already-approved expense -> 409 (immutability enforced)" \
  || bad "Reject edit of approved expense" "$CODE"

# ---------------------------------------------------------------------------
step "6. What's still a TODO stub (expected 500 with a clear message, not a crash)"
# ---------------------------------------------------------------------------

CODE=$(expect_code "revenue (blocked on Epic 3)" GET "/api/finance/revenue" '' "$FIN")
MSG=$(cat /tmp/demo_last_body | json "['message']" 2>/dev/null || echo "?")
echo "  /api/finance/revenue -> HTTP $CODE : $MSG"

CODE=$(expect_code "analytics dashboard (blocked on Epic 1/2/3)" GET "/api/analytics/dashboard" '' "$FIN")
MSG=$(cat /tmp/demo_last_body | json "['message']" 2>/dev/null || echo "?")
echo "  /api/analytics/dashboard -> HTTP $CODE : $MSG"

# ---------------------------------------------------------------------------
step "Summary"
# ---------------------------------------------------------------------------
echo "  Passed: $PASS   Failed: $FAIL"
[ "$FAIL" -eq 0 ] && echo "  All demoed behaviors passed." || echo "  Some checks failed — see FAIL lines above."

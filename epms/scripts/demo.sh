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

# Pick a Python that actually runs (on Windows, "python3" may be a Store stub).
PY=""
for c in python3 python; do
  if "$c" -c "pass" >/dev/null 2>&1; then PY="$c"; break; fi
done
[ -n "$PY" ] || { echo "python3/python is required for JSON parsing"; exit 1; }

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
json() { "$PY" -c "import sys,json; d=json.load(sys.stdin, parse_float=str); print(d$1)"; }

# Use fresh, randomized names/ids each run so the script is safely
# re-runnable against the same long-lived dev server without tripping
# uniqueness constraints or the "one active agreement per author+book"
# rule from a previous run's leftover data.
RUN_ID=$RANDOM
DEMO_AUTHOR_ID=$((RANDOM + 1000))
# Book 1 is part of the dummy Epic 3 catalogue seeded by the dev profile, so it
# has completed sales to calculate royalties from (see DemoSalesDataInitializer).
DEMO_BOOK_ID=1

# Royalty period = the previous calendar month (always inside the 12 months of
# seeded sales, and never in the future).
PERIOD_START=$("$PY" -c "import datetime as d; t=d.date.today().replace(day=1); print((t-d.timedelta(days=1)).replace(day=1))")
PERIOD_END=$("$PY" -c "import datetime as d; t=d.date.today().replace(day=1); print(t-d.timedelta(days=1))")

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

CODE=$(expect_code "edit category" PUT "/api/categories/$CATEGORY_ID" "{\"categoryName\":\"Demo Fiction $RUN_ID (edited)\",\"description\":\"Edited by demo script\"}" "$ADM")
[ "$CODE" = "200" ] && ok "Edit category -> 200" || bad "Edit category" "$CODE"

CODE=$(expect_code "delete category" DELETE "/api/categories/$CATEGORY_ID" '' "$ADM")
[ "$CODE" = "200" ] && ok "Delete category -> 200" || bad "Delete category" "$CODE"

CODE=$(expect_code "delete category in use" DELETE "/api/categories/1" '' "$ADM")
[ "$CODE" = "409" ] && ok "Delete a category that books still use -> 409 (protects the store catalogue)" || bad "Delete in-use category" "$CODE"

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
  "{\"authorId\":$DEMO_AUTHOR_ID,\"bookId\":$DEMO_BOOK_ID,\"royaltyPercentage\":10,\"effectiveDate\":\"2024-01-01\"}" "$FIN")
[ "$CODE" = "200" ] && ok "Create royalty agreement (DRAFT) -> 200" || bad "Create agreement" "$CODE"
AGREEMENT_ID=$(cat /tmp/demo_last_body | json "['data']['royaltyAgreementId']")

CALC_BODY="{\"royaltyAgreementId\":$AGREEMENT_ID,\"periodStart\":\"$PERIOD_START\",\"periodEnd\":\"$PERIOD_END\",\"deductions\":0}"

CODE=$(expect_code "calculate before activation is rejected" POST "/api/royalties/calculate" "$CALC_BODY" "$FIN")
[ "$CODE" = "409" ] && ok "Calculate against a DRAFT agreement -> 409 (no active agreement)" || bad "Reject calc on DRAFT" "$CODE"

CODE=$(expect_code "activate agreement" POST "/api/royalty-agreements/$AGREEMENT_ID/activate" '' "$FIN")
[ "$CODE" = "200" ] && ok "Activate royalty agreement -> 200" || bad "Activate agreement" "$CODE"

# ---------------------------------------------------------------------------
step "4. Royalty Calculation Engine (US45, US42, US43) — from completed sales"
# ---------------------------------------------------------------------------

# What Epic 4 received from Epic 3 for this book + period (only COMPLETED counts)
CODE=$(expect_code "completed sales for book" GET "/api/finance/sales/summary?bookId=$DEMO_BOOK_ID&from=$PERIOD_START&to=$PERIOD_END" '' "$FIN")
EXPECTED_SOLD=$(cat /tmp/demo_last_body | json "['data']['booksSold']")
EXPECTED_GROSS=$(cat /tmp/demo_last_body | json "['data']['grossSales']")
[ "$CODE" = "200" ] && ok "Completed sales for book $DEMO_BOOK_ID, $PERIOD_START..$PERIOD_END: $EXPECTED_SOLD books, gross $EXPECTED_GROSS" \
  || bad "Sales summary" "$CODE"

CODE=$(expect_code "calculate royalty" POST "/api/royalties/calculate" "$CALC_BODY" "$FIN")
CHECK=$(cat /tmp/demo_last_body | "$PY" -c "
import sys, json
from decimal import Decimal, ROUND_HALF_UP
d = json.load(sys.stdin, parse_float=str)['data']
gross = Decimal(str(d['grossSales'])); amount = Decimal(str(d['royaltyAmount']))
expected = (gross * Decimal('10') / Decimal('100')).quantize(Decimal('0.01'), ROUND_HALF_UP)
ok = str(d['booksSold']) == '$EXPECTED_SOLD' and gross == Decimal('$EXPECTED_GROSS') and amount == expected
print(('OK' if ok else 'MISMATCH') + ' sold=%s gross=%s royalty=%s expected=%s' % (d['booksSold'], gross, amount, expected))
" 2>/dev/null || echo "MISMATCH (no body)")
if [ "$CODE" = "200" ] && [[ "$CHECK" == OK* ]]; then
  ok "Calculate royalty from completed sales x 10%: $CHECK (BigDecimal, verified exact)"
else
  bad "Royalty calculation" "code=$CODE $CHECK"
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
step "6. Revenue Monitoring (US38) — from completed sales received from Epic 3"
# ---------------------------------------------------------------------------

CODE=$(expect_code "revenue last 12 months" GET "/api/finance/revenue" '' "$FIN")
if [ "$CODE" = "200" ]; then
  SUMMARY=$(cat /tmp/demo_last_body | "$PY" -c "
import sys, json
d = json.load(sys.stdin, parse_float=str)['data']
x = d['excluded']
print('total=%s completedSales=%s booksSold=%s months=%d cancelledExcluded=%s returnedExcluded=%s' % (
    d['totalRevenue'], d['completedSales'], d['booksSold'], len(d['monthlyRevenue']), x['cancelledCount'], x['returnedCount']))")
  ok "Revenue (trailing 12 months): $SUMMARY"
else
  bad "Revenue" "$CODE"
fi

CODE=$(expect_code "revenue for the royalty period" GET "/api/finance/revenue?from=$PERIOD_START&to=$PERIOD_END" '' "$FIN")
[ "$CODE" = "200" ] && ok "Revenue filtered to $PERIOD_START..$PERIOD_END -> 200" || bad "Revenue (filtered)" "$CODE"

CODE=$(expect_code "revenue with inverted period" GET "/api/finance/revenue?from=$PERIOD_END&to=$PERIOD_START" '' "$FIN")
[ "$CODE" = "409" ] && ok "Revenue with end-before-start period -> 409" || bad "Revenue period validation" "$CODE"

CODE=$(expect_code "raw sales feed" GET "/api/finance/sales?from=$PERIOD_START&to=$PERIOD_END&status=CANCELLED" '' "$FIN")
[ "$CODE" = "200" ] && ok "Raw sales feed (cancelled only, for audit of what was excluded) -> 200" || bad "Sales feed" "$CODE"

# ---------------------------------------------------------------------------
step "7. What's still a TODO stub (expected 500 with a clear message, not a crash)"
# ---------------------------------------------------------------------------

CODE=$(expect_code "analytics dashboard (blocked on Epic 1/2/3)" GET "/api/analytics/dashboard" '' "$FIN")
MSG=$(cat /tmp/demo_last_body | json "['message']" 2>/dev/null || echo "?")
echo "  /api/analytics/dashboard -> HTTP $CODE : $MSG"

# ---------------------------------------------------------------------------
step "Summary"
# ---------------------------------------------------------------------------
echo "  Passed: $PASS   Failed: $FAIL"
[ "$FAIL" -eq 0 ] && echo "  All demoed behaviors passed." || echo "  Some checks failed — see FAIL lines above."

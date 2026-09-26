#!/usr/bin/env bash
#
# End-to-end demo/test script for Epic 4 (Administration, Finance & Royalty
# Management). Walks through every feature with visible PASS/FAIL output.
#
# Needs a running app on a database loaded with docs/demo-data.sql:
#   1. mysql -u root -p < docs/epms-schema.sql
#   2. cd epms && bash mvnw spring-boot:run      (Flyway applies V400; leave it running)
#   3. mysql -u root -p epms < docs/demo-data.sql
#   4. bash epms/scripts/demo.sh
#
# Safe to re-run: each run picks a sales period that has not been calculated
# yet for the demo agreement, and uses fresh names / references.
#
set -uo pipefail

BASE="${BASE_URL:-http://localhost:8080}"
PASS=0
FAIL=0

PY=""
for c in python3 python; do
  if "$c" -c "pass" >/dev/null 2>&1; then PY="$c"; break; fi
done
[ -n "$PY" ] || { echo "python3/python is required for JSON parsing"; exit 1; }

step() { printf "\n\033[1;34m== %s ==\033[0m\n" "$1"; }
ok()   { PASS=$((PASS+1)); printf "  \033[1;32mPASS\033[0m %s\n" "$1"; }
bad()  { FAIL=$((FAIL+1)); printf "  \033[1;31mFAIL\033[0m %s (got: %s)\n" "$1" "$2"; }

# call METHOD URL TOKEN [JSON] -> prints HTTP code, body saved in $BODY_FILE
BODY_FILE=$(mktemp)
call() {
  local method="$1" url="$2" token="$3" data="${4:-}"
  local args=(-s -o "$BODY_FILE" -w "%{http_code}" -X "$method" "$BASE$url")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$data" ] && args+=(-H "Content-Type: application/json" -d "$data")
  curl "${args[@]}"
}
json() { "$PY" -c "import sys,json; d=json.load(open('$BODY_FILE'), parse_float=str); print($1)"; }
expect() { # expect DESC EXPECTED_CODE ACTUAL_CODE
  if [ "$2" = "$3" ]; then ok "$1 ($3)"; else bad "$1, expected $2" "$3 $(head -c 200 "$BODY_FILE")"; fi
}
login() {
  curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
    -d "{\"email\":\"$1\",\"password\":\"Password@123\"}" \
    | "$PY" -c "import sys,json; print(json.load(sys.stdin)['data']['token'])" 2>/dev/null
}
d() { "$PY" -c "import datetime as t; print($1)"; }

RUN_ID=$RANDOM
TODAY=$(d "t.date.today()")

step "0. Log in as each role (accounts from docs/demo-data.sql)"
ADMIN=$(login admin@readingplanet.lk); FIN=$(login finance@readingplanet.lk); EXEC=$(login exec@readingplanet.lk)
AUTH1=$(login author1@readingplanet.lk); AUTH2=$(login author2@readingplanet.lk)
for v in ADMIN FIN EXEC AUTH1 AUTH2; do
  if [ -n "${!v}" ]; then ok "$v logged in"; else bad "$v login" "no token (is docs/demo-data.sql loaded?)"; fi
done
[ -n "$FIN" ] || { echo "Cannot continue without the demo accounts."; exit 1; }

step "1. Administration: categories, settings, announcements (US31 - US34)"
expect "Public category list (no login)" 200 "$(call GET /api/categories "")"
expect "Create category" 200 "$(call POST /api/categories "$ADMIN" "{\"categoryName\":\"Demo $RUN_ID\"}")"
expect "Duplicate category name (any case) rejected" 409 "$(call POST /api/categories "$ADMIN" "{\"categoryName\":\"demo $RUN_ID\"}")"
expect "Finance staff cannot create categories" 403 "$(call POST /api/categories "$FIN" "{\"categoryName\":\"X$RUN_ID\"}")"
expect "Tax rate outside 0-100 rejected" 400 "$(call PUT /api/settings "$ADMIN" '{"settingKey":"taxRatePercent","settingValue":"150"}')"
expect "Set tax rate to 18%" 200 "$(call PUT /api/settings "$ADMIN" '{"settingKey":"taxRatePercent","settingValue":"18"}')"
expect "Settings change history (audit)" 200 "$(call GET /api/settings/history "$ADMIN")"
expect "Public settings for other epics" 200 "$(call GET /api/settings/public "")"
expect "Announcement to authors" 200 "$(call POST /api/announcements "$ADMIN" "{\"title\":\"Statements $RUN_ID\",\"content\":\"Statements issued\",\"audience\":\"AUTHOR\"}")"
call GET /api/announcements/active "$AUTH1" >/dev/null
if json "any(a['title']=='Statements $RUN_ID' for a in d['data'])" | grep -q True; then ok "Author sees the announcement"; else bad "Author sees the announcement" "missing"; fi

step "2. Revenue from completed sales only (US35)"
expect "Revenue, last 12 months" 200 "$(call GET /api/finance/revenue "$FIN")"
echo "     net revenue $(json "d['data']['totalRevenue']"), completed sales $(json "d['data']['completedSales']"), excluded cancelled $(json "d['data']['excluded']['cancelledCount']") / returned $(json "d['data']['excluded']['returnedCount']")"
expect "Revenue filtered to bookstore (wholesale)" 200 "$(call GET "/api/finance/revenue?channel=BOOKSTORE" "$FIN")"
expect "Period ending before it starts rejected" 400 "$(call GET "/api/finance/revenue?from=2026-05-01&to=2026-01-01" "$FIN")"

step "3. Royalty agreements (US41)"
expect "Agreement for a book of another author rejected" 400 "$(call POST /api/royalty-agreements "$FIN" '{"authorId":9001,"bookId":9003,"royaltyPercentage":10,"effectiveDate":"2026-01-01"}')"
expect "Rate above 50% rejected" 400 "$(call POST /api/royalty-agreements "$FIN" '{"authorId":9001,"bookId":9002,"royaltyPercentage":60,"effectiveDate":"2026-01-01"}')"
expect "Executive cannot create agreements" 403 "$(call POST /api/royalty-agreements "$EXEC" '{"authorId":9001,"bookId":9002,"royaltyPercentage":10,"effectiveDate":"2026-01-01"}')"

step "4. Royalty calculation with preview and duplicate guard (US42, US43)"
# Find a month (going back from last month) not yet calculated for RA-DEMO-001
PERIOD=""
for back in $(seq 1 11); do
  S=$("$PY" -c "
import datetime as t
m=t.date.today().replace(day=1)
for _ in range($back): m=(m-t.timedelta(days=1)).replace(day=1)
e=(m.replace(day=28)+t.timedelta(days=4)).replace(day=1)-t.timedelta(days=1)
print(m, e)")
  set -- $S
  code=$(call POST /api/royalties/preview "$FIN" "{\"royaltyAgreementId\":9001,\"periodStart\":\"$1\",\"periodEnd\":\"$2\"}")
  if [ "$code" = "200" ]; then PERIOD="$1 $2"; break; fi
done
if [ -z "$PERIOD" ]; then bad "Find an uncalculated month" "every month already calculated (reload demo data)"; else
  set -- $PERIOD; PS=$1; PE=$2
  ok "Preview for $PS to $PE: gross royalty $(json "d['data']['calculation']['royaltyAmount']"), $(json "len(d['data']['lines'])") lines, nothing saved"
  expect "Calculate and save" 200 "$(call POST /api/royalties/calculate "$FIN" "{\"royaltyAgreementId\":9001,\"periodStart\":\"$PS\",\"periodEnd\":\"$PE\"}")"
  CALC=$(json "d['data']['calculationId']"); PAYABLE=$(json "d['data']['payableAmount']")
  expect "Same period again rejected (duplicate)" 409 "$(call POST /api/royalties/calculate "$FIN" "{\"royaltyAgreementId\":9001,\"periodStart\":\"$PS\",\"periodEnd\":\"$PE\"}")"
  expect "Overlapping period rejected" 409 "$(call POST /api/royalties/calculate "$FIN" "{\"royaltyAgreementId\":9001,\"periodStart\":\"$PE\",\"periodEnd\":\"$PE\"}")"
  expect "Future period rejected" 400 "$(call POST /api/royalties/calculate "$FIN" "{\"royaltyAgreementId\":9001,\"periodStart\":\"$TODAY\",\"periodEnd\":\"2099-01-01\"}")"

  step "5. Statement, approval and payment (US44 - US46)"
  expect "Statement not available before it is issued" 409 "$(call GET /api/royalties/$CALC/statement "$FIN")"
  expect "Issue statement" 200 "$(call POST /api/royalties/$CALC/statement "$FIN")"
  echo "     statement $(json "d['data']['statementNumber']")"
  expect "Executive cannot approve" 403 "$(call POST /api/royalties/$CALC/approve "$EXEC")"
  expect "Cannot pay before approval" 409 "$(call POST /api/royalties/$CALC/pay "$FIN" "{\"transactionReference\":\"BT-$RUN_ID\",\"paymentDate\":\"$TODAY\",\"paymentMethod\":\"BANK_TRANSFER\",\"amount\":$PAYABLE}")"
  expect "Approve" 200 "$(call POST /api/royalties/$CALC/approve "$FIN")"
  STATUS=$(json "d['data']['status']")
  if [ "$STATUS" = "APPROVED" ]; then
    expect "Wrong amount rejected" 409 "$(call POST /api/royalties/$CALC/pay "$FIN" "{\"transactionReference\":\"BT-$RUN_ID\",\"paymentDate\":\"$TODAY\",\"paymentMethod\":\"BANK_TRANSFER\",\"amount\":1}")"
    expect "Record payment" 200 "$(call POST /api/royalties/$CALC/pay "$FIN" "{\"transactionReference\":\"BT-$RUN_ID\",\"paymentDate\":\"$TODAY\",\"paymentMethod\":\"BANK_TRANSFER\",\"amount\":$PAYABLE}")"
    expect "Paid record is immutable" 409 "$(call POST /api/royalties/$CALC/pay "$FIN" "{\"transactionReference\":\"BT2-$RUN_ID\",\"paymentDate\":\"$TODAY\",\"paymentMethod\":\"BANK_TRANSFER\",\"amount\":$PAYABLE}")"
  else
    ok "Payable $PAYABLE is below the payment threshold, carried forward ($STATUS)"
  fi

  step "6. Author sees only their own royalties (US47)"
  expect "Author 1 opens own statement" 200 "$(call GET /api/me/royalty/statements/$CALC "$AUTH1")"
  expect "Author 2 cannot open author 1's statement" 403 "$(call GET /api/me/royalty/statements/$CALC "$AUTH2")"
  expect "Author cannot use the finance API" 403 "$(call GET /api/royalties/$CALC/statement "$AUTH1")"
fi

step "7. Expenses, invoices and payments (US36 - US38)"
expect "Record expense" 200 "$(call POST /api/finance/expenses "$FIN" "{\"category\":\"MARKETING\",\"amount\":2500,\"expenseDate\":\"$TODAY\",\"description\":\"Demo $RUN_ID\"}")"
EXP=$(json "d['data']['expenseId']")
expect "Invalid expense category rejected" 400 "$(call POST /api/finance/expenses "$FIN" "{\"category\":\"FOOD\",\"amount\":1,\"expenseDate\":\"$TODAY\"}")"
expect "Approve expense" 200 "$(call POST /api/finance/expenses/$EXP/approve "$FIN")"
expect "Approved expense cannot be edited" 409 "$(call PUT /api/finance/expenses/$EXP "$FIN" "{\"category\":\"MARKETING\",\"amount\":1,\"expenseDate\":\"$TODAY\"}")"
expect "Create invoice" 200 "$(call POST /api/invoices "$FIN" '{"customerName":"Sarasavi Bookshop","lines":[{"description":"Kandy Rain","quantity":10,"unitPrice":1500}]}')"
INV=$(json "d['data']['invoice']['invoiceId']"); TOTAL=$(json "d['data']['invoice']['totalAmount']")
echo "     $(json "d['data']['invoice']['invoiceNumber']"), total $TOTAL incl. tax $(json "d['data']['invoice']['taxRate']")%"
expect "Issue invoice" 200 "$(call POST /api/invoices/$INV/issue "$FIN")"
expect "Issued invoice cannot be edited" 409 "$(call PUT /api/invoices/$INV "$FIN" '{"customerName":"X","lines":[{"description":"a","quantity":1,"unitPrice":1}]}')"
expect "Payment above balance rejected" 409 "$(call POST /api/invoices/$INV/payments "$FIN" "{\"amount\":99999999,\"paymentDate\":\"$TODAY\",\"paymentMethod\":\"CASH\",\"reference\":\"R-$RUN_ID\"}")"
expect "Part payment" 200 "$(call POST /api/invoices/$INV/payments "$FIN" "{\"amount\":1000,\"paymentDate\":\"$TODAY\",\"paymentMethod\":\"CASH\",\"reference\":\"R-$RUN_ID\"}")"
echo "     status $(json "d['data']['invoice']['status']"), outstanding $(json "d['data']['invoice']['outstanding']")"
expect "Duplicate payment reference rejected" 409 "$(call POST /api/invoices/$INV/payments "$FIN" "{\"amount\":1,\"paymentDate\":\"$TODAY\",\"paymentMethod\":\"CASH\",\"reference\":\"R-$RUN_ID\"}")"

step "8. Financial reports and finalization (US39, US40)"
FROM=$(d "(t.date.today().replace(day=1) - t.timedelta(days=330)).replace(day=1)")
expect "Generate profit and loss" 200 "$(call POST "/api/reports/generate?reportType=PROFIT_AND_LOSS&periodStart=$FROM&periodEnd=$TODAY" "$FIN")"
REP=$(json "d['data']['report']['reportId']")
echo "     net revenue $(json "d['data']['figures']['netRevenue']"), profit $(json "d['data']['figures']['profit']")"
expect "Finalize" 200 "$(call POST /api/reports/$REP/finalize "$FIN")"
expect "Finalized report cannot be deleted" 409 "$(call DELETE /api/reports/$REP "$FIN")"
expect "Finalized report cannot be regenerated" 409 "$(call POST /api/reports/$REP/regenerate "$FIN")"
expect "Executive can download it" 200 "$(call GET /api/reports/$REP/download "$EXEC")"

step "9. Executive analytics (US48 - US50)"
for e in dashboard sales books authors royalties; do
  expect "Analytics: $e" 200 "$(call GET /api/analytics/$e "$EXEC")"
done
expect "Authors cannot open analytics" 403 "$(call GET /api/analytics/dashboard "$AUTH1")"

step "Summary"
printf "  \033[1;32m%d passed\033[0m, \033[1;31m%d failed\033[0m\n" "$PASS" "$FAIL"
rm -f "$BODY_FILE"
[ "$FAIL" -eq 0 ]

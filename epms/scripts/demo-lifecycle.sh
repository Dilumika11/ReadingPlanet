#!/usr/bin/env bash
#
# End-to-end demo of Epics 1-3 (and the hand-over to Epic 4): one book from
# author sign-up to a delivered customer order that shows up in revenue.
#
#   author profile + manuscript (US1-US7) -> chief editor assigns (US11)
#   -> editor asks for a revision (US13, US14) -> author uploads it (US6)
#   -> editor accepts -> designer uploads versions (US16, US17)
#   -> author approves the design (US18) -> production QC + ready (US19, US20)
#   -> print job (US21, US22) -> stock received (US23) and adjusted (US24)
#   -> customer searches, fills a cart, orders (US25-US27)
#   -> warehouse packs, dispatches, delivers (US28) -> customer tracks it (US29)
#   -> wholesale order approved and delivered (US30) -> revenue (Epic 4)
#
# Needs a running app on a database loaded with docs/demo-data.sql (for the
# admin account, categories and genres). Safe to re-run: every run creates new
# accounts and a new book.
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

BODY=$(mktemp); TMPD=$(mktemp -d)
call() { # METHOD URL TOKEN [JSON]
  local args=(-s -o "$BODY" -w "%{http_code}" -X "$1" "$BASE$2")
  [ -n "$3" ] && args+=(-H "Authorization: Bearer $3")
  [ -n "${4:-}" ] && args+=(-H "Content-Type: application/json" -d "$4")
  curl "${args[@]}"
}
upload() { # URL TOKEN field=@file ...
  local url="$1" tok="$2"; shift 2
  local args=(-s -o "$BODY" -w "%{http_code}" -X POST "$BASE$url" -H "Authorization: Bearer $tok")
  for f in "$@"; do args+=(-F "$f"); done
  curl "${args[@]}"
}
json() { "$PY" -c "import sys,json; d=json.load(open('$BODY'), parse_float=str); print($1)"; }
expect() { if [ "$2" = "$3" ]; then ok "$1 ($3)"; else bad "$1, expected $2" "$3 $(head -c 220 "$BODY")"; fi; }
login() {
  curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
    -d "{\"email\":\"$1\",\"password\":\"$2\"}" | "$PY" -c "import sys,json; print(json.load(sys.stdin)['data']['token'])" 2>/dev/null
}

R=$RANDOM$RANDOM
PW='Passw0rd!'
TODAY=$("$PY" -c "import datetime; print(datetime.date.today())")
NEXTWEEK=$("$PY" -c "import datetime; print(datetime.date.today()+datetime.timedelta(days=7))")

# Tiny sample files
printf '%%PDF-1.4\n%% sample manuscript\n' > "$TMPD/manuscript.pdf"
printf '%%PDF-1.4\n%% revised manuscript\n' > "$TMPD/revised.pdf"
printf '%%PDF-1.4\n%% layout\n' > "$TMPD/layout.pdf"
printf '%%PDF-1.4\n%% print ready\n' > "$TMPD/print.pdf"
"$PY" -c "import base64,sys; open(sys.argv[1],'wb').write(base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg=='))" "$TMPD/cover.png"

step "0. Accounts: public sign-up is limited, staff are created by the admin"
ADMIN=$(login admin@readingplanet.lk 'Password@123')
[ -n "$ADMIN" ] || { echo "Load docs/demo-data.sql first (admin account needed)."; exit 1; }
expect "Sign-up as ADMIN refused" 200 "$(call POST /api/auth/register "" "{\"username\":\"hack$R\",\"firstName\":\"H\",\"lastName\":\"X\",\"email\":\"hack$R@x.lk\",\"password\":\"$PW\",\"role\":\"ADMIN\"}")"
[ "$(json "d['success']")" = "False" ] && ok "  ...and the account was not created" || bad "ADMIN sign-up refused" "$(head -c 200 "$BODY")"
expect "Author signs up" 200 "$(call POST /api/auth/register "" "{\"username\":\"author$R\",\"firstName\":\"Nadee\",\"lastName\":\"Perera\",\"email\":\"author$R@x.lk\",\"password\":\"$PW\",\"role\":\"AUTHOR\"}")"
expect "Customer signs up" 200 "$(call POST /api/auth/register "" "{\"username\":\"cust$R\",\"firstName\":\"Kasun\",\"lastName\":\"Silva\",\"email\":\"cust$R@x.lk\",\"password\":\"$PW\",\"role\":\"CUSTOMER\"}")"
for role in CHIEF_EDITOR EDITOR DESIGNER PRODUCTION_MANAGER INVENTORY_STAFF SALES_STAFF; do
  lc=$(echo "$role" | tr 'A-Z_' 'a-z-')
  expect "Admin creates $role account" 200 "$(call POST /api/admin/users "$ADMIN" "{\"username\":\"$lc$R\",\"firstName\":\"$role\",\"lastName\":\"Demo\",\"email\":\"$lc$R@x.lk\",\"password\":\"$PW\",\"role\":\"$role\"}")"
done
AUTH=$(login "author$R@x.lk" "$PW"); CUST=$(login "cust$R@x.lk" "$PW")
CHIEF=$(login "chief-editor$R@x.lk" "$PW"); EDITOR=$(login "editor$R@x.lk" "$PW"); DESIGNER=$(login "designer$R@x.lk" "$PW")
PM=$(login "production-manager$R@x.lk" "$PW"); WH=$(login "inventory-staff$R@x.lk" "$PW"); SALES=$(login "sales-staff$R@x.lk" "$PW")
call GET /api/editorial/editors "$CHIEF" >/dev/null
EDITOR_ID=$(json "[e['userId'] for e in d['data'] if e['fullName']=='EDITOR Demo'][-1]")

step "1. Epic 1: profile, manuscript, files, submission (US1-US4)"
expect "Profile exists from sign-up" 200 "$(call GET /api/author/profile "$AUTH")"
expect "Create a draft manuscript" 200 "$(call POST /api/author/manuscripts "$AUTH" '{"title":"Monsoon Diaries","genreId":9001,"synopsis":"A family story set in Galle.","language":"Sinhala","wordCount":62000}')"
MS=$(json "d['data']['manuscriptId']"); echo "     manuscript $(json "d['data']['manuscriptCode']")"
expect "Submit without a file refused" 409 "$(call POST /api/author/manuscripts/$MS/submit "$AUTH")"
expect "Upload the manuscript (PDF)" 200 "$(upload /api/author/manuscripts/$MS/files "$AUTH" "file=@$TMPD/manuscript.pdf;type=application/pdf" "category=MANUSCRIPT")"
expect "Upload a supporting file" 200 "$(upload /api/author/manuscripts/$MS/files "$AUTH" "file=@$TMPD/layout.pdf;type=application/pdf" "category=SUPPORTING")"
printf 'x' > "$TMPD/bad.exe"
expect "Disallowed file type refused" 400 "$(upload /api/author/manuscripts/$MS/files "$AUTH" "file=@$TMPD/bad.exe;type=application/x-msdownload")"
expect "Submit with an incomplete profile refused" 409 "$(call POST /api/author/manuscripts/$MS/submit "$AUTH")"
expect "Complete the profile" 200 "$(call PUT /api/author/profile "$AUTH" '{"firstName":"Nadee","lastName":"Perera","phoneNumber":"+94 77 123 4567","biography":"Short story writer from Galle.","nationality":"Sri Lankan"}')"
expect "Submit" 200 "$(call POST /api/author/manuscripts/$MS/submit "$AUTH")"
expect "Author tracks status" 200 "$(call GET /api/author/manuscripts/$MS "$AUTH")"
echo "     status $(json "d['data']['manuscript']['status']"), $(json "len(d['data']['files'])") files, $(json "len(d['data']['history'])") history entries"
expect "Editor cannot see an unassigned manuscript" 403 "$(call GET /api/editorial/manuscripts/$MS "$EDITOR")"

step "2. Epic 2: assignment, review, revision (US11-US15)"
expect "Editor cannot assign" 403 "$(call POST "/api/editorial/manuscripts/$MS/assign?editorId=$EDITOR_ID" "$EDITOR")"
expect "Chief editor assigns the editor" 200 "$(call POST "/api/editorial/manuscripts/$MS/assign?editorId=$EDITOR_ID" "$CHIEF")"
expect "Editor sees it in their queue" 200 "$(call GET /api/editorial/my-manuscripts "$EDITOR")"
expect "Revision without a deadline refused" 400 "$(call POST /api/editorial/manuscripts/$MS/decision "$EDITOR" '{"decision":"REVISION_REQUIRED","comments":"Tighten chapter 3."}')"
expect "Editor requests a revision" 200 "$(call POST /api/editorial/manuscripts/$MS/decision "$EDITOR" "{\"decision\":\"REVISION_REQUIRED\",\"comments\":\"Tighten chapter 3 and fix the ending.\",\"revisionDeadline\":\"$NEXTWEEK\"}")"
call GET /api/author/manuscripts/$MS "$AUTH" >/dev/null
echo "     author sees: \"$(json "d['data']['revisions'][-1]['editorComments']")\" due $(json "d['data']['revisions'][-1]['responseDeadline']")"
expect "Author uploads the revised manuscript (US6)" 200 "$(upload /api/author/manuscripts/$MS/revision "$AUTH" "file=@$TMPD/revised.pdf;type=application/pdf" "notes=Chapter 3 rewritten")"
call GET /api/author/manuscripts/$MS "$AUTH" >/dev/null
echo "     version history: $(json "', '.join('v%s %s' % (f['fileVersion'], f['fileCategory']) for f in d['data']['files'])")"
expect "Editor accepts" 200 "$(call POST /api/editorial/manuscripts/$MS/decision "$EDITOR" '{"decision":"ACCEPT","comments":"Ready for production."}')"
expect "Chief editor monitors progress" 200 "$(call GET /api/editorial/overview "$CHIEF")"

step "3. Epic 2: design versions, author approval, QC, ready for printing (US16-US20)"
expect "Designer uploads cover only (v1)" 200 "$(upload /api/design/manuscripts/$MS/versions "$DESIGNER" "cover=@$TMPD/cover.png;type=image/png" "notes=First cover")"
D1=$(json "d['data']['designId']")
expect "Incomplete design cannot go to the author" 409 "$(call POST /api/design/designs/$D1/submit "$DESIGNER")"
expect "Designer adds layout and print PDF (v2, cover carried over)" 200 "$(upload /api/design/manuscripts/$MS/versions "$DESIGNER" "layout=@$TMPD/layout.pdf;type=application/pdf" "printFile=@$TMPD/print.pdf;type=application/pdf")"
D2=$(json "d['data']['designId']")
expect "Send v2 to the author" 200 "$(call POST /api/design/designs/$D2/submit "$DESIGNER")"
expect "Author sees the design waiting" 200 "$(call GET /api/author/designs "$AUTH")"
expect "Author previews the cover" 200 "$(call GET /api/documents/designs/$D2/cover "$AUTH")"
expect "Rejecting without comments refused" 400 "$(call POST /api/author/designs/$D2/decision "$AUTH" '{"approve":false}')"
expect "Author approves the design" 200 "$(call POST /api/author/designs/$D2/decision "$AUTH" '{"approve":true,"comments":"Lovely"}')"
expect "Ready for printing before QC refused" 409 "$(call POST /api/production/manuscripts/$MS/ready-for-printing "$PM" '{"isbn":"978-955-0000-00-0","price":1500,"categoryId":9001}')"
expect "QC failing an item needs notes" 400 "$(call POST /api/production/manuscripts/$MS/quality-check "$PM" '{"checklist":{"coverResolution":true,"layoutMatchesManuscript":true,"printFileValid":false,"metadataCorrect":true,"proofread":true}}')"
expect "QC passes" 200 "$(call POST /api/production/manuscripts/$MS/quality-check "$PM" '{"checklist":{"coverResolution":true,"layoutMatchesManuscript":true,"printFileValid":true,"metadataCorrect":true,"proofread":true}}')"
ISBN="978-955-$((R % 9000 + 1000))-$((R % 90 + 10))-$((R % 9))"
expect "Mark ready for printing (creates the book)" 200 "$(call POST /api/production/manuscripts/$MS/ready-for-printing "$PM" "{\"isbn\":\"$ISBN\",\"price\":1500,\"categoryId\":9001,\"edition\":\"1st\",\"totalPages\":240}")"
BOOK=$(json "d['data']['bookId']")

step "4. Epic 3: print job and stock (US21-US24)"
expect "Create print job" 200 "$(call POST /api/production/print-orders "$PM" "{\"bookId\":$BOOK,\"quantity\":200,\"printingCompany\":\"Sarasavi Printers\",\"expectedCompletionDate\":\"$NEXTWEEK\"}")"
PO=$(json "d['data']['printOrderId']")
expect "Schedule it" 200 "$(call PUT /api/production/print-orders/$PO "$PM" "{\"quantity\":200,\"printingCompany\":\"Sarasavi Printers\",\"expectedCompletionDate\":\"$NEXTWEEK\",\"status\":\"SCHEDULED\"}")"
expect "Skipping to COMPLETED refused" 409 "$(call PUT /api/production/print-orders/$PO "$PM" '{"status":"COMPLETED"}')"
expect "Printing in progress" 200 "$(call PUT /api/production/print-orders/$PO "$PM" '{"status":"IN_PROGRESS"}')"
expect "Printing completed" 200 "$(call PUT /api/production/print-orders/$PO "$PM" '{"status":"COMPLETED"}')"
expect "Receiving more than printed refused" 409 "$(call POST /api/warehouse/inventory/receive "$WH" "{\"printOrderId\":$PO,\"quantityReceived\":500}")"
expect "Receive 200 copies" 200 "$(call POST /api/warehouse/inventory/receive "$WH" "{\"printOrderId\":$PO,\"quantityReceived\":200,\"warehouseLocation\":\"Rack B2\"}")"
expect "Record 3 damaged copies" 200 "$(call POST /api/warehouse/inventory/adjust "$WH" "{\"bookId\":$BOOK,\"adjustmentType\":\"DAMAGED\",\"quantity\":3,\"remarks\":\"Water damage in transit\"}")"
call GET /api/warehouse/inventory "$WH" >/dev/null
echo "     in stock: $(json "[i['quantityInStock'] for i in d['data'] if i['bookId']==$BOOK][0]")"

step "5. Epic 3: customer search, cart, order, tracking (US25-US27, US29)"
expect "Search the store" 200 "$(call GET "/api/public/books?q=monsoon%20diaries&inStock=true" "")"
LISTING=$(json "[b['bookId'] for b in d['data'] if b['isbn']=='$ISBN'][0]")
echo "     listed as catalogue book #$LISTING"
expect "Add 2 to the cart" 200 "$(call POST /api/customer/cart "$CUST" "{\"catalogBookId\":$LISTING,\"quantity\":2}")"
expect "More than 50 copies of one title refused" 400 "$(call POST /api/customer/cart "$CUST" "{\"catalogBookId\":$LISTING,\"quantity\":49}")"
expect "Staff cannot use a customer cart" 403 "$(call GET /api/customer/cart "$WH")"
expect "Place the order" 200 "$(call POST /api/customer/orders "$CUST" '{"recipientName":"Kasun Silva","recipientPhone":"0771234567","shippingAddress":"12 Temple Road, Kandy"}')"
ORDER=$(json "d['data']['order']['customerOrderId']"); echo "     order $(json "d['data']['order']['orderNumber']"), total $(json "d['data']['order']['totalAmount']")"
call GET /api/warehouse/inventory "$WH" >/dev/null
echo "     reserved now: $(json "[i['quantityReserved'] for i in d['data'] if i['bookId']==$BOOK][0]")"

step "6. Epic 3: warehouse fulfilment (US28)"
expect "Dispatch before packing refused" 409 "$(call POST /api/warehouse/orders/$ORDER/dispatch "$WH" '{"shippingProvider":"Pronto","trackingNumber":"T1"}')"
expect "Pack" 200 "$(call POST /api/warehouse/orders/$ORDER/pack "$WH")"
expect "Dispatch" 200 "$(call POST /api/warehouse/orders/$ORDER/dispatch "$WH" "{\"shippingProvider\":\"Pronto Couriers\",\"trackingNumber\":\"PR$R\",\"shippingCost\":350}")"
expect "Customer cannot cancel after dispatch" 409 "$(call POST /api/customer/orders/$ORDER/cancel "$CUST")"
expect "Deliver" 200 "$(call POST /api/warehouse/orders/$ORDER/deliver "$WH")"
expect "Customer tracks the delivery" 200 "$(call GET /api/customer/orders/$ORDER "$CUST")"
echo "     $(json "' > '.join(s['label'] for s in d['data']['timeline'] if s['done'])")"

step "7. Epic 3: wholesale order with approval (US30)"
expect "Add a bookstore" 200 "$(call POST /api/sales/bookstores "$SALES" "{\"bookstoreName\":\"Vijitha Yapa $R\",\"email\":\"vy$R@x.lk\",\"city\":\"Colombo\"}")"
STORE=$(json "d['data']['bookstoreId']")
expect "Create wholesale order (50 copies)" 200 "$(call POST /api/sales/bookstore-orders "$SALES" "{\"bookstoreId\":$STORE,\"items\":[{\"bookId\":$BOOK,\"quantity\":50}]}")"
BO=$(json "d['data']['order']['bookstoreOrderId']")
expect "Warehouse cannot dispatch before approval" 409 "$(call POST /api/warehouse/bookstore-orders/$BO/dispatch "$WH" '{"shippingProvider":"Own van"}')"
expect "Sales manager approves (stock reserved)" 200 "$(call POST /api/sales/bookstore-orders/$BO/approve "$SALES")"
expect "Dispatch wholesale order" 200 "$(call POST /api/warehouse/bookstore-orders/$BO/dispatch "$WH" '{"shippingProvider":"Own van"}')"
expect "Deliver wholesale order" 200 "$(call POST /api/warehouse/bookstore-orders/$BO/deliver "$WH")"
expect "Second order rejected with a reason" 200 "$(call POST /api/sales/bookstore-orders "$SALES" "{\"bookstoreId\":$STORE,\"items\":[{\"bookId\":$BOOK,\"quantity\":10}]}")"
BO2=$(json "d['data']['order']['bookstoreOrderId']")
expect "  reject" 200 "$(call POST /api/sales/bookstore-orders/$BO2/reject "$SALES" '{"reason":"Credit limit reached"}')"

step "8. Hand-over to Epic 4: delivered orders are revenue"
FIN=$(login finance@readingplanet.lk 'Password@123')
expect "Revenue for today" 200 "$(call GET "/api/finance/revenue?from=$TODAY&to=$TODAY&bookId=$BOOK" "$FIN")"
echo "     book #$BOOK today: $(json "d['data']['completedSales']") completed sales, $(json "d['data']['booksSold']") books, net $(json "d['data']['totalRevenue']")"
[ "$(json "d['data']['booksSold']")" = "52" ] && ok "Retail 2 + wholesale 50 counted" || bad "Revenue counts delivered copies" "$(json "d['data']['booksSold']")"

step "Summary"
printf "  \033[1;32m%d passed\033[0m, \033[1;31m%d failed\033[0m\n" "$PASS" "$FAIL"
rm -rf "$BODY" "$TMPD"
[ "$FAIL" -eq 0 ]

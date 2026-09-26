/*
 * Printable invoice and royalty statement pages. They read the staff token
 * saved at login; authors open their statements through /api/me/royalty so
 * they can only ever load their own.
 */
(function () {
    "use strict";

    const token = localStorage.getItem("rp_token") || sessionStorage.getItem("rp_token");
    let user = null;
    try { user = JSON.parse(localStorage.getItem("rp_user") || sessionStorage.getItem("rp_user")); } catch (e) { /* ignore */ }
    const doc = document.getElementById("doc");
    const id = new URLSearchParams(window.location.search).get("id");

    function h(s) {
        return s == null ? "" : String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }
    function money(v, cur) {
        const n = Number(v);
        return (cur ? cur + " " : "") + (isNaN(n) ? "-" : n.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 }));
    }
    function fail(msg) { doc.innerHTML = '<p class="error">' + h(msg) + "</p>"; }

    async function get(url) {
        const res = await fetch(url, { headers: { "Authorization": "Bearer " + token } });
        const data = await res.json().catch(function () { return {}; });
        if (res.status === 401) { window.location.replace("/admin-login.html"); throw new Error("Please log in"); }
        if (!res.ok) throw new Error(data.message || ("Request failed (" + res.status + ")"));
        return data.data;
    }

    if (!token) { window.location.replace("/admin-login.html"); return; }
    if (!id) { fail("No document selected."); return; }

    const isAuthor = user && user.roles && user.roles.indexOf("AUTHOR") !== -1;

    if (document.body.dataset.doc === "invoice") {
        get("/api/invoices/" + id).then(function (d) {
            const i = d.invoice, cur = i.currency;
            document.title = "Invoice " + i.invoiceNumber;
            let lines = "";
            d.lines.forEach(function (l) {
                lines += "<tr><td>" + h(l.description) + '</td><td class="num">' + l.quantity + '</td><td class="num">' + money(l.unitPrice) +
                    '</td><td class="num">' + money(l.lineTotal) + "</td></tr>";
            });
            let pays = "";
            d.payments.forEach(function (p) {
                pays += "<tr><td>" + h(String(p.paymentDate).substring(0, 10)) + "</td><td>" + h(p.paymentMethod) + "</td><td>" + h(p.referenceNumber) +
                    '</td><td class="num">' + money(p.amount) + "</td></tr>";
            });
            doc.innerHTML =
                '<div class="head"><div><h1>' + h(d.companyName) + "</h1><p>" + h(d.companyAddress) + '</p></div><div class="right"><h2>INVOICE</h2><p><strong>' +
                h(i.invoiceNumber) + "</strong><br>Date " + h(i.invoiceDate) + (i.dueDate ? "<br>Due " + h(i.dueDate) : "") + "<br>Status " + h(i.status.replace(/_/g, " ")) + "</p></div></div>" +
                '<div class="box"><strong>Bill to</strong><br>' + h(i.customerName) + "<br>" + h(i.customerAddress || "") + "<br>" + h(i.customerEmail || "") + "</div>" +
                '<table><thead><tr><th>Description</th><th class="num">Qty</th><th class="num">Unit price</th><th class="num">Amount</th></tr></thead><tbody>' + lines + "</tbody></table>" +
                '<table class="totals"><tr><td>Subtotal</td><td class="num">' + money(i.amount, cur) + "</td></tr><tr><td>Tax (" + i.taxRate + '%)</td><td class="num">' + money(i.taxAmount, cur) +
                '</td></tr><tr class="grand"><td>Total</td><td class="num">' + money(i.totalAmount, cur) + '</td></tr><tr><td>Paid</td><td class="num">' + money(i.amountPaid, cur) +
                '</td></tr><tr class="grand"><td>Balance due</td><td class="num">' + money(i.outstanding, cur) + "</td></tr></table>" +
                (pays ? "<h3>Payments received</h3><table><thead><tr><th>Date</th><th>Method</th><th>Reference</th><th class=\"num\">Amount</th></tr></thead><tbody>" + pays + "</tbody></table>" : "") +
                (i.notes ? '<p class="notes">' + h(i.notes) + "</p>" : "");
        }).catch(function (e) { fail(e.message); });
    } else {
        get(isAuthor ? "/api/me/royalty/statements/" + id : "/api/royalties/" + id + "/statement").then(function (s) {
            const cur = s.currency;
            document.title = "Royalty Statement " + s.statementNumber;
            let lines = "";
            s.lines.forEach(function (l) {
                lines += "<tr" + (l.lineType === "RETURN" ? ' class="ret"' : "") + "><td>" + h(l.saleDate) + "</td><td>" + h(l.saleReference || "") + "</td><td>" +
                    h(l.channel) + (l.lineType === "RETURN" ? " (returned)" : "") + '</td><td class="num">' + l.quantity + '</td><td class="num">' + money(l.baseAmount) +
                    '</td><td class="num">' + l.rateApplied + '%</td><td class="num">' + money(l.royaltyAmount) + "</td></tr>";
            });
            const units = Object.keys(s.unitsByChannel || {}).map(function (k) {
                return (k === "BOOKSTORE" ? "Bookstore (wholesale)" : "Retail") + ": " + s.unitsByChannel[k];
            }).join(", ");
            doc.innerHTML =
                '<div class="head"><div><h1>' + h(s.companyName) + "</h1><p>" + h(s.companyAddress) + '</p></div><div class="right"><h2>ROYALTY STATEMENT</h2><p><strong>' +
                h(s.statementNumber) + "</strong><br>Issued " + h(String(s.issuedAt || "").substring(0, 10)) + "<br>Status " + h(s.status.replace(/_/g, " ")) + "</p></div></div>" +
                '<div class="grid2"><div class="box"><strong>Author</strong><br>' + h(s.authorName) + "<br>" + h(s.authorEmail || "") + '</div><div class="box"><strong>Book</strong><br>' +
                h(s.bookTitle) + (s.isbn ? "<br>ISBN " + h(s.isbn) : "") + "<br>Period " + h(s.periodStart) + " to " + h(s.periodEnd) + "</div></div>" +
                '<div class="box"><strong>Agreement terms</strong> ' + h(s.agreementNumber) + ": " + s.royaltyRate + "% of " + (s.basis === "LIST_PRICE" ? "list price" : "net sales") +
                (s.wholesaleRate != null ? ", " + s.wholesaleRate + "% on bookstore (wholesale) sales" : "") + ", paid " + h(String(s.paymentFrequency || "").toLowerCase()) +
                (Number(s.advanceAmount) > 0 ? ", advance " + money(s.advanceAmount, cur) : "") + "</div>" +
                '<table class="totals"><tr><td>Units sold</td><td class="num">' + s.unitsSold + (units ? " (" + h(units) + ")" : "") + "</td></tr>" +
                '<tr><td>Units returned (no royalty)</td><td class="num">' + s.unitsReturned + "</td></tr>" +
                '<tr><td>Net sales</td><td class="num">' + money(s.grossSales, cur) + "</td></tr>" +
                (Number(s.deductions) > 0 ? '<tr><td>Deductions</td><td class="num">' + money(s.deductions, cur) + "</td></tr>" : "") +
                '<tr><td>Royalty base</td><td class="num">' + money(s.royaltyBase, cur) + "</td></tr>" +
                '<tr class="grand"><td>Gross royalty</td><td class="num">' + money(s.grossRoyalty, cur) + "</td></tr>" +
                '<tr><td>Less advance recouped</td><td class="num">- ' + money(s.advanceRecouped, cur) + "</td></tr>" +
                (Number(s.carriedForwardIn) > 0 ? '<tr><td>Plus carried forward from earlier periods</td><td class="num">' + money(s.carriedForwardIn, cur) + "</td></tr>" : "") +
                '<tr class="grand"><td>Payable</td><td class="num">' + money(s.payableAmount, cur) + "</td></tr>" +
                (s.paidOn ? "<tr><td>Paid</td><td class=\"num\">" + h(s.paidOn) + ", " + h(String(s.paymentMethod || "").replace("_", " ")) + ", ref " + h(s.paymentReference) + "</td></tr>" : "") +
                "</table><h3>How the royalty was calculated</h3>" +
                '<table><thead><tr><th>Date</th><th>Sale</th><th>Channel</th><th class="num">Qty</th><th class="num">Base</th><th class="num">Rate</th><th class="num">Royalty</th></tr></thead><tbody>' +
                lines + "</tbody></table>";
        }).catch(function (e) { fail(e.message); });
    }
})();

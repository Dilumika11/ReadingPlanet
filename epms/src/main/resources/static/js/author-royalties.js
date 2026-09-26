/*
 * Author "My Royalties" page (US47). Everything comes from /api/me/royalty,
 * which resolves the author from the login, so an author only ever sees
 * their own agreements, statements and payments.
 */
(function () {
    "use strict";

    const token = localStorage.getItem("rp_token") || sessionStorage.getItem("rp_token");
    let user = null;
    try { user = JSON.parse(localStorage.getItem("rp_user") || sessionStorage.getItem("rp_user")); } catch (e) { /* ignore */ }
    if (!token || !user) { window.location.replace("/admin-login.html"); return; }

    const content = document.getElementById("content");
    document.getElementById("headerUserName").textContent = user.fullName || user.email;
    document.getElementById("logoutBtn").onclick = function () {
        ["rp_token", "rp_user"].forEach(function (k) { localStorage.removeItem(k); sessionStorage.removeItem(k); });
        window.location.replace("/admin-login.html");
    };

    function h(s) {
        return s == null ? "" : String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }
    function money(v) {
        const n = Number(v);
        return isNaN(n) ? "-" : "Rs " + n.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
    function badge(s) { return '<span class="badge badge-' + String(s).toLowerCase() + '">' + h(String(s).replace(/_/g, " ")) + "</span>"; }
    function table(headers, rows, empty) {
        return '<div class="table-wrap"><table class="data-table"><thead><tr>' + headers.map(function (x) { return "<th>" + x + "</th>"; }).join("") +
            "</tr></thead><tbody>" + (rows || '<tr><td colspan="' + headers.length + '" class="empty-state">' + h(empty) + "</td></tr>") + "</tbody></table></div>";
    }

    async function get(url) {
        const res = await fetch(url, { headers: { "Authorization": "Bearer " + token } });
        const data = await res.json().catch(function () { return {}; });
        if (res.status === 401) { window.location.replace("/admin-login.html"); throw new Error("Please log in"); }
        if (!res.ok) throw new Error(data.message || ("Request failed (" + res.status + ")"));
        return data.data;
    }

    Promise.all([get("/api/me/royalty/statements"), get("/api/me/royalty/payments"), get("/api/me/royalty/agreements"),
        get("/api/announcements/active").catch(function () { return []; })])
        .then(function (r) {
            const statements = r[0] || [], payments = r[1] || [], agreements = r[2] || [], news = r[3] || [];
            const paid = payments.filter(function (p) { return p.paymentStatus === "PAID"; })
                .reduce(function (s, p) { return s + Number(p.amount); }, 0);
            const pending = statements.filter(function (s) { return ["STATEMENT_ISSUED", "APPROVED"].indexOf(s.calculation.status) >= 0; })
                .reduce(function (s, x) { return s + Number(x.calculation.payableAmount); }, 0);

            let stRows = "";
            statements.forEach(function (s) {
                const c = s.calculation;
                stRows += "<tr><td><strong>" + h(c.statementNumber) + "</strong></td><td>" + h(s.bookTitle) + "</td><td>" + h(c.salesPeriodStart) + " to " +
                    h(c.salesPeriodEnd) + "</td><td>" + c.booksSold + "</td><td>" + money(c.royaltyAmount) + "</td><td><strong>" + money(c.payableAmount) +
                    "</strong></td><td>" + badge(c.status) + '</td><td><a class="btn-icon" target="_blank" title="View statement" href="/admin/royalty-statement.html?id=' +
                    c.calculationId + '"><i class="fas fa-file-invoice-dollar"></i></a></td></tr>';
            });
            let payRows = "";
            payments.forEach(function (p) {
                payRows += "<tr><td>" + h(p.paymentReference) + "</td><td>" + money(p.amount) + "</td><td>" + badge(p.paymentStatus) + "</td><td>" +
                    (p.paymentStatus === "PAID" ? h(p.paymentDate) + ", " + h(String(p.paymentMethod || "").replace("_", " ")) : "-") + "</td></tr>";
            });
            let agRows = "";
            agreements.forEach(function (a) {
                agRows += "<tr><td>" + h(a.agreementNumber) + "</td><td>Book #" + a.bookId + "</td><td>" + a.royaltyPercentage + "%" +
                    (a.wholesaleRoyaltyPercentage != null ? " (" + a.wholesaleRoyaltyPercentage + "% wholesale)" : "") + "</td><td>" +
                    money(a.advanceAmount) + "</td><td>" + h(a.effectiveDate) + " to " + h(a.expiryDate || "open") + "</td><td>" + badge(a.status) + "</td></tr>";
            });
            let newsHtml = "";
            news.forEach(function (n) {
                newsHtml += '<div class="alert alert-info"><strong>' + h(n.title) + "</strong><br>" + h(n.content) + "</div>";
            });

            content.innerHTML = newsHtml +
                '<div class="stats-grid"><div class="stat-card"><div class="stat-label">Royalties Paid to You</div><div class="stat-value">' + money(paid) +
                '</div></div><div class="stat-card"><div class="stat-label">Awaiting Payment</div><div class="stat-value">' + money(pending) +
                '</div></div><div class="stat-card"><div class="stat-label">Statements</div><div class="stat-value">' + statements.length + "</div></div></div>" +
                '<div class="content-card"><h2>Royalty Statements</h2>' + table(["Statement", "Book", "Period", "Books Sold", "Royalty", "Payable", "Status", ""], stRows,
                    "No statements have been issued to you yet.") + "</div>" +
                '<div class="content-card"><h2>Payments</h2>' + table(["Payment", "Amount", "Status", "Paid"], payRows, "No payments yet.") + "</div>" +
                '<div class="content-card"><h2>My Agreements</h2>' + table(["Agreement", "Book", "Rate", "Advance", "Dates", "Status"], agRows, "No agreements.") + "</div>";
        })
        .catch(function (err) {
            content.innerHTML = '<div class="alert alert-error">' + h(err.message) + "</div>";
        });
})();

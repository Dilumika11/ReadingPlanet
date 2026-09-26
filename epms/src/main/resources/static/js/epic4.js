/*
 * Epic 4 screens (Administration, Finance & Royalty Management) for the
 * shared staff dashboard. Each screen registers itself in window.RPViews;
 * admin-dashboard.js routes to it and provides the shared helpers as
 * window.RP (api, money, escapeHtml, date validation, ...).
 *
 * Business rules live in the API; these screens only pre-check input so
 * the user gets feedback before a round trip, then show the API's message.
 */
(function () {
    "use strict";

    const V = window.RPViews = window.RPViews || {};

    // ---------- small helpers ----------

    function R() { return window.RP; }
    function h(s) { return R().escapeHtml(s); }
    function money(v) { return R().money(v); }
    function canWrite() { return R().isFinanceStaff; }

    function badge(status) {
        const s = String(status || "");
        return '<span class="badge badge-' + s.toLowerCase() + '">' + h(s.replace(/_/g, " ")) + "</span>";
    }

    function loading(title) {
        R().pageTitle.textContent = title;
        R().content.innerHTML = '<div class="empty-state"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';
    }

    function fail(err) {
        R().content.innerHTML = '<div class="alert alert-error">' + h(err.message) + "</div>";
    }

    function alertHtml(type, msg) {
        return '<div class="alert alert-' + type + '">' + h(msg) + "</div>";
    }

    function showMsg(id, type, msg) {
        const el = document.getElementById(id);
        if (el) el.innerHTML = msg ? alertHtml(type, msg) : "";
        if (el && msg) el.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }

    function card(title, inner, desc, extraClass) {
        return '<div class="content-card' + (extraClass ? " " + extraClass : "") + '">' +
            (title ? "<h2>" + title + "</h2>" : "") +
            (desc ? '<p class="card-desc">' + desc + "</p>" : "") + inner + "</div>";
    }

    function table(headers, rows, emptyText) {
        return '<div class="table-wrap"><table class="data-table"><thead><tr>' +
            headers.map(function (x) { return "<th>" + x + "</th>"; }).join("") +
            "</tr></thead><tbody>" +
            (rows || '<tr><td colspan="' + headers.length + '" class="empty-state">' + h(emptyText || "Nothing here yet.") + "</td></tr>") +
            "</tbody></table></div>";
    }

    function stat(label, value) {
        return '<div class="stat-card"><div class="stat-label">' + h(label) + '</div><div class="stat-value">' + value + "</div></div>";
    }

    function field(label, inputHtml, opts) {
        opts = opts || {};
        return '<div class="form-group' + (opts.full ? " full" : "") + '"><label>' + h(label) +
            (opts.required ? ' <span class="req">*</span>' : "") + "</label>" + inputHtml +
            (opts.hint ? '<div class="hint">' + h(opts.hint) + "</div>" : "") + "</div>";
    }

    function options(list, selected, placeholder) {
        let html = placeholder != null ? '<option value="">' + h(placeholder) + "</option>" : "";
        list.forEach(function (o) {
            const value = typeof o === "object" ? o.value : o;
            const label = typeof o === "object" ? o.label : String(o).replace(/_/g, " ");
            html += '<option value="' + h(value) + '"' + (String(value) === String(selected) ? " selected" : "") + ">" + h(label) + "</option>";
        });
        return html;
    }

    function val(id) {
        const el = document.getElementById(id);
        return el ? el.value.trim() : "";
    }

    function iconBtn(cls, title, icon, attrs) {
        return '<button type="button" class="btn-icon ' + cls + '" title="' + h(title) + '" ' + (attrs || "") +
            '><i class="fas ' + icon + '"></i></button> ';
    }

    function on(selector, handler) {
        R().content.querySelectorAll(selector).forEach(function (el) {
            el.addEventListener("click", function () { handler(el.getAttribute("data-id"), el); });
        });
    }

    async function post(url, body) {
        return R().api(url, { method: "POST", body: body == null ? undefined : JSON.stringify(body) });
    }

    /** Runs an action and re-renders, or shows the API's message in the given box. */
    async function act(msgId, fn, after) {
        try {
            await fn();
            if (after) after();
        } catch (err) {
            showMsg(msgId, "error", err.message);
        }
    }

    function askReason(what) {
        const r = window.prompt("Reason to " + what + ":");
        if (r === null) return null;
        if (!r.trim()) { window.alert("A reason is required."); return null; }
        return r.trim();
    }

    async function downloadWithAuth(url, fallbackName) {
        const res = await fetch(url, { headers: { "Authorization": "Bearer " + R().getToken() } });
        if (!res.ok) {
            const data = await res.json().catch(function () { return {}; });
            throw new Error(data.message || ("Download failed (" + res.status + ")"));
        }
        const blob = await res.blob();
        const disposition = res.headers.get("Content-Disposition") || "";
        const m = /filename="?([^";]+)"?/.exec(disposition);
        const a = document.createElement("a");
        a.href = URL.createObjectURL(blob);
        a.download = m ? m[1] : fallbackName;
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(function () { URL.revokeObjectURL(a.href); }, 5000);
    }

    async function openWithAuth(url) {
        const res = await fetch(url, { headers: { "Authorization": "Bearer " + R().getToken() } });
        if (!res.ok) throw new Error("Could not open the file (" + res.status + ")");
        const blob = await res.blob();
        window.open(URL.createObjectURL(blob), "_blank");
    }

    function readOnlyNote() {
        return canWrite() ? "" : '<div class="alert alert-info">You have read-only access to this screen. Changes are made by finance staff.</div>';
    }

    function dateRangeForm(id, from, to, extra) {
        return '<form id="' + id + '" novalidate><div class="form-grid">' +
            field("From", '<input type="date" id="from" value="' + from + '">') +
            field("To", '<input type="date" id="to" value="' + to + '">') + (extra || "") +
            '</div><div class="form-actions"><button type="submit" class="btn btn-primary"><i class="fas fa-filter"></i> Apply</button></div>' +
            '<div id="filterMsg"></div></form>';
    }

    function bindDateRange(id, onApply) {
        R().linkDateRange("from", "to", { maxToday: true });
        document.getElementById(id).onsubmit = function (e) {
            e.preventDefault();
            const f = val("from"), t = val("to");
            const err = R().validateFilterRange(f, t);
            R().setFieldError("to", err);
            showMsg("filterMsg", "error", err);
            if (!err) onApply(f, t);
        };
    }

    // ===================== DASHBOARD =====================

    V["epic4-dashboard"] = async function () {
        loading(R().isAdmin ? "Admin Dashboard" : (R().isExecutive ? "Overview" : "Finance Dashboard"));
        try {
            const stats = ((await R().api("/api/admin/dashboard")) || {}).data || {};
            let rev = null;
            try { rev = ((await R().api("/api/finance/revenue")) || {}).data; } catch (ignored) { /* optional */ }
            let html = '<div class="stats-grid">' +
                stat("Categories", stats.totalCategories) + stat("Genres", stats.totalGenres) +
                stat("Announcements", stats.totalAnnouncements) + stat("Active Royalty Agreements", stats.activeRoyaltyAgreements) +
                "</div>";
            html += '<div class="stats-grid">' +
                stat("Statements Awaiting Approval", stats.statementsAwaitingApproval) +
                stat("Approved Royalties to Pay", stats.approvedRoyaltiesToPay) +
                stat("Pending Expenses", stats.pendingExpenses) +
                stat("Royalties Calculated", money(stats.totalRoyaltiesCalculated || 0)) + "</div>";
            if (rev) {
                html += '<div class="stats-grid">' +
                    stat("Net Revenue (last 12 months)", money(rev.totalRevenue)) +
                    stat("Completed Sales", rev.completedSales) + stat("Books Sold", rev.booksSold) +
                    stat("Average Sale", money(rev.averageSaleValue)) + "</div>";
            }
            R().content.innerHTML = html;
        } catch (err) { fail(err); }
    };

    // ===================== REVENUE (US35) =====================

    V["revenue"] = async function (filter) {
        filter = filter || {};
        const from = filter.from || R().monthStartISO(11), to = filter.to || R().todayISO();
        loading("Revenue");
        try {
            let q = "/api/finance/revenue?from=" + from + "&to=" + to;
            if (filter.channel) q += "&channel=" + encodeURIComponent(filter.channel);
            if (filter.categoryId) q += "&categoryId=" + filter.categoryId;
            const [revRes, catRes] = await Promise.all([R().api(q), R().api("/api/categories")]);
            const rev = revRes.data || {}, cats = catRes.data || [], excluded = rev.excluded || {}, channels = rev.revenueByChannel || {};

            let monthRows = "";
            (rev.monthlyRevenue || []).forEach(function (m) {
                monthRows += "<tr><td>" + h(m.month) + "</td><td>" + m.completedSales + "</td><td>" + m.booksSold +
                    "</td><td><strong>" + money(m.revenue) + "</strong></td></tr>";
            });
            let bookRows = "";
            (rev.topBooks || []).forEach(function (b) {
                bookRows += "<tr><td>#" + b.bookId + "</td><td>" + h(b.bookTitle) + "</td><td>" + b.booksSold +
                    "</td><td><strong>" + money(b.revenue) + "</strong></td></tr>";
            });
            let channelRows = "";
            Object.keys(channels).forEach(function (k) {
                channelRows += "<tr><td>" + h(k === "BOOKSTORE" ? "BOOKSTORE (wholesale)" : "CUSTOMER (retail)") +
                    "</td><td><strong>" + money(channels[k]) + "</strong></td></tr>";
            });

            const extra = field("Channel", '<select id="channel">' + options(
                [{ value: "CUSTOMER", label: "Customer (retail)" }, { value: "BOOKSTORE", label: "Bookstore (wholesale)" }],
                filter.channel, "All channels") + "</select>") +
                field("Category", '<select id="categoryId">' + options(cats.map(function (c) {
                    return { value: c.categoryId, label: c.categoryName };
                }), filter.categoryId, "All categories") + "</select>");

            R().content.innerHTML =
                card(null, dateRangeForm("filterForm", from, to, extra)) +
                '<div class="stats-grid">' + stat("Net Revenue", money(rev.totalRevenue)) +
                stat("Gross (before discounts)", money(rev.grossRevenue)) + stat("Completed Sales", rev.completedSales) +
                stat("Books Sold", rev.booksSold) + "</div>" +
                card("Monthly Revenue", table(["Month", "Completed Sales", "Books Sold", "Net Revenue"], monthRows, "No completed sales in this period.")) +
                card("Top Books", table(["Book", "Title", "Books Sold", "Net Revenue"], bookRows, "No data.")) +
                card("Revenue by Channel", table(["Channel", "Net Revenue"], channelRows, "No data.")) +
                card("Excluded from Revenue", table(["Status", "Transactions", "Amount"],
                    "<tr><td>" + badge("CANCELLED") + "</td><td>" + (excluded.cancelledCount || 0) + "</td><td>" + money(excluded.cancelledAmount || 0) + "</td></tr>" +
                    "<tr><td>" + badge("RETURNED") + "</td><td>" + (excluded.returnedCount || 0) + "</td><td>" + money(excluded.returnedAmount || 0) + "</td></tr>"),
                    "Received from Epic 3 but not counted: only completed sales contribute to revenue and royalties.");

            bindDateRange("filterForm", function (f, t) {
                V["revenue"]({ from: f, to: t, channel: val("channel"), categoryId: val("categoryId") });
            });
        } catch (err) { fail(err); }
    };

    // ===================== EXPENSES (US36) =====================

    V["expenses"] = async function (editId) {
        loading("Expenses");
        try {
            const [expRes, catRes] = await Promise.all([R().api("/api/finance/expenses"), R().api("/api/finance/expenses/categories")]);
            const items = expRes.data || [], cats = catRes.data || [];
            const editing = editId ? items.find(function (e) { return e.expenseId === editId; }) : null;

            let rows = "";
            items.forEach(function (e) {
                let actions = "";
                if (e.receiptFile) actions += iconBtn("btn-receipt", "View receipt", "fa-paperclip", 'data-id="' + e.expenseId + '"');
                if (canWrite()) {
                    if (e.status === "RECORDED") {
                        actions += iconBtn("btn-edit", "Edit", "fa-edit", 'data-id="' + e.expenseId + '"') +
                            iconBtn("btn-upload", "Attach receipt", "fa-upload", 'data-id="' + e.expenseId + '"') +
                            iconBtn("btn-review", "Mark reviewed", "fa-eye", 'data-id="' + e.expenseId + '"');
                    }
                    if (e.status === "RECORDED" || e.status === "REVIEWED") {
                        actions += iconBtn("btn-approve", "Approve", "fa-check", 'data-id="' + e.expenseId + '"') +
                            iconBtn("btn-reject", "Reject", "fa-times", 'data-id="' + e.expenseId + '"');
                    }
                    if (e.status === "APPROVED") actions += iconBtn("btn-post", "Post to ledger", "fa-book", 'data-id="' + e.expenseId + '"');
                    if (e.status === "RECORDED") actions += iconBtn("btn-delete", "Delete", "fa-trash", 'data-id="' + e.expenseId + '"');
                }
                rows += "<tr><td>#" + e.expenseId + "</td><td>" + h(e.expenseDate) + "</td><td>" + h(e.category) +
                    "</td><td>" + h(e.description || "") + (e.rejectionReason ? '<div class="muted">Rejected: ' + h(e.rejectionReason) + "</div>" : "") +
                    "</td><td><strong>" + money(e.amount) + "</strong></td><td>" + badge(e.status) +
                    '</td><td class="actions-cell">' + (actions || '<span class="muted">-</span>') + "</td></tr>";
            });

            const form = canWrite() ? card(editing ? "Edit Expense #" + editing.expenseId : "Record Expense",
                '<form id="expenseForm" novalidate><div class="form-grid">' +
                field("Category", '<select id="category">' + options(cats, editing && editing.category, "Select...") + "</select>", { required: true }) +
                field("Amount", '<input type="number" id="amount" min="0.01" step="0.01" value="' + (editing ? editing.amount : "") + '">', { required: true }) +
                field("Date", '<input type="date" id="expenseDate" max="' + R().todayISO() + '" value="' + (editing ? editing.expenseDate : R().todayISO()) + '">', { required: true }) +
                field("Description", '<input type="text" id="description" maxlength="255" value="' + h(editing ? editing.description || "" : "") + '">', { full: true }) +
                (editing ? "" : field("Receipt (PDF, JPG or PNG, max 5 MB)", '<input type="file" id="receipt" accept=".pdf,.jpg,.jpeg,.png">', { full: true })) +
                '</div><div class="form-actions"><button type="submit" class="btn btn-primary"><i class="fas ' + (editing ? "fa-save" : "fa-plus") + '"></i> ' +
                (editing ? "Save Changes" : "Record") + "</button>" +
                (editing ? '<button type="button" class="btn btn-secondary" id="cancelEdit">Cancel</button>' : "") + "</div></form>",
                "Only pending (RECORDED) expenses can be edited or deleted. Approved and posted expenses count in the expense and profit and loss reports.") : "";

            R().content.innerHTML = '<div id="formMessage"></div>' + readOnlyNote() + form +
                card("All Expenses (" + items.length + ")", '<div id="listMessage"></div><input type="file" id="receiptPicker" accept=".pdf,.jpg,.jpeg,.png" hidden>' +
                    table(["ID", "Date", "Category", "Description", "Amount", "Status", "Actions"], rows, "No expenses recorded yet."));

            if (canWrite()) {
                document.getElementById("expenseForm").onsubmit = async function (e) {
                    e.preventDefault();
                    const body = { category: val("category"), amount: Number(val("amount")), expenseDate: val("expenseDate"), description: val("description") || null };
                    if (!body.category || !val("amount") || !body.expenseDate) { showMsg("formMessage", "error", "Category, amount and date are required."); return; }
                    if (!(body.amount > 0)) { R().setFieldError("amount", "Amount must be greater than 0."); return; }
                    const fileInput = document.getElementById("receipt");
                    const file = fileInput && fileInput.files[0];
                    if (file && file.size > 5 * 1024 * 1024) { showMsg("formMessage", "error", "Receipts must be 5 MB or smaller."); return; }
                    await act("formMessage", async function () {
                        if (editing) {
                            await R().api("/api/finance/expenses/" + editing.expenseId, { method: "PUT", body: JSON.stringify(body) });
                        } else {
                            const created = await post("/api/finance/expenses", body);
                            if (file) {
                                const fd = new FormData(); fd.append("file", file);
                                await R().apiUpload("/api/finance/expenses/" + created.data.expenseId + "/receipt", fd);
                            }
                        }
                    }, function () { V["expenses"](); });
                };
                const cancel = document.getElementById("cancelEdit");
                if (cancel) cancel.onclick = function () { V["expenses"](); };
            }

            let uploadFor = null;
            const picker = document.getElementById("receiptPicker");
            picker.onchange = async function () {
                const file = picker.files[0];
                if (!file || !uploadFor) return;
                const fd = new FormData(); fd.append("file", file);
                await act("listMessage", function () { return R().apiUpload("/api/finance/expenses/" + uploadFor + "/receipt", fd); },
                    function () { V["expenses"](); });
            };
            on(".btn-upload", function (id) { uploadFor = id; picker.value = ""; picker.click(); });
            on(".btn-receipt", function (id) { act("listMessage", function () { return openWithAuth("/api/finance/expenses/" + id + "/receipt"); }); });
            on(".btn-edit", function (id) { V["expenses"](Number(id)); window.scrollTo({ top: 0, behavior: "smooth" }); });
            on(".btn-review", function (id) { act("listMessage", function () { return post("/api/finance/expenses/" + id + "/review"); }, function () { V["expenses"](); }); });
            on(".btn-approve", function (id) { act("listMessage", function () { return post("/api/finance/expenses/" + id + "/approve"); }, function () { V["expenses"](); }); });
            on(".btn-post", function (id) { act("listMessage", function () { return post("/api/finance/expenses/" + id + "/post"); }, function () { V["expenses"](); }); });
            on(".btn-reject", function (id) {
                const reason = askReason("reject this expense");
                if (reason) act("listMessage", function () { return post("/api/finance/expenses/" + id + "/reject", { reason: reason }); }, function () { V["expenses"](); });
            });
            on(".btn-delete", function (id) {
                if (window.confirm("Delete expense #" + id + "? This cannot be undone.")) {
                    act("listMessage", function () { return R().api("/api/finance/expenses/" + id, { method: "DELETE" }); }, function () { V["expenses"](); });
                }
            });
        } catch (err) { fail(err); }
    };

    // ===================== INVOICES (US37) & PAYMENTS (US38) =====================

    function lineRowHtml(l) {
        l = l || {};
        return '<div class="form-grid invoice-line">' +
            field("Description", '<input type="text" class="l-desc" maxlength="255" value="' + h(l.description || "") + '">', { required: true }) +
            field("Qty", '<input type="number" class="l-qty" min="1" step="1" value="' + (l.quantity || 1) + '">', { required: true }) +
            field("Unit price", '<input type="number" class="l-price" min="0" step="0.01" value="' + (l.unitPrice != null ? l.unitPrice : "") + '">', { required: true }) +
            '<div class="form-group"><label>&nbsp;</label><button type="button" class="btn btn-secondary l-remove"><i class="fas fa-minus"></i> Remove</button></div></div>';
    }

    V["invoices"] = async function (editId) {
        loading("Invoices");
        try {
            const [invRes, setRes] = await Promise.all([R().api("/api/invoices"), R().api("/api/settings/public")]);
            const items = invRes.data || [], settings = setRes.data || {};
            let editing = null;
            if (editId) editing = ((await R().api("/api/invoices/" + editId)) || {}).data;

            let rows = "";
            items.forEach(function (i) {
                rows += "<tr><td><a href=\"#\" class=\"open-invoice\" data-id=\"" + i.invoiceId + "\"><strong>" + h(i.invoiceNumber) +
                    "</strong></a></td><td>" + h(i.invoiceDate) + "</td><td>" + h(i.customerName || "-") + "</td><td>" + money(i.totalAmount) +
                    "</td><td>" + money(i.outstanding) + "</td><td>" + badge(i.status) + '</td><td class="actions-cell">' +
                    iconBtn("btn-open", "Open", "fa-folder-open", 'data-id="' + i.invoiceId + '"') +
                    '<a class="btn-icon" title="Print" target="_blank" href="/admin/invoice-print.html?id=' + i.invoiceId + '"><i class="fas fa-print"></i></a>' +
                    (canWrite() && i.status === "DRAFT" ? " " + iconBtn("btn-edit", "Edit", "fa-edit", 'data-id="' + i.invoiceId + '"') : "") +
                    "</td></tr>";
            });

            const inv = editing && editing.invoice;
            const form = canWrite() ? card(inv ? "Edit Draft " + h(inv.invoiceNumber) : "New Invoice",
                '<form id="invoiceForm" novalidate><div class="form-grid">' +
                field("Customer name", '<input type="text" id="customerName" maxlength="150" value="' + h(inv ? inv.customerName : "") + '">', { required: true }) +
                field("Customer email", '<input type="email" id="customerEmail" maxlength="150" value="' + h(inv ? inv.customerEmail || "" : "") + '">') +
                field("Customer address", '<input type="text" id="customerAddress" maxlength="255" value="' + h(inv ? inv.customerAddress || "" : "") + '">', { full: true }) +
                field("Invoice date", '<input type="date" id="invoiceDate" value="' + (inv ? inv.invoiceDate : R().todayISO()) + '">') +
                field("Due date", '<input type="date" id="dueDate" value="' + (inv && inv.dueDate ? inv.dueDate : "") + '">') +
                field("Notes", '<input type="text" id="notes" maxlength="500" value="' + h(inv ? inv.notes || "" : "") + '">', { full: true }) +
                '</div><h3 class="sub-heading">Line items</h3><div id="lines"></div>' +
                '<button type="button" class="btn btn-secondary" id="addLine"><i class="fas fa-plus"></i> Add line</button>' +
                '<p class="card-desc" id="invoiceTotals"></p>' +
                '<div class="form-actions"><button type="submit" class="btn btn-primary"><i class="fas fa-save"></i> ' + (inv ? "Save Draft" : "Create Draft") + "</button>" +
                (inv ? '<button type="button" class="btn btn-secondary" id="cancelEdit">Cancel</button>' : "") + "</div></form>",
                "The invoice number, currency (" + h(settings.currency) + ") and tax rate (" + h(settings.taxRatePercent) +
                "%) are fixed when the invoice is created. Only drafts can be edited.") : "";

            R().content.innerHTML = '<div id="formMessage"></div>' + readOnlyNote() + form +
                card("All Invoices (" + items.length + ")", '<div id="listMessage"></div>' +
                    table(["Number", "Date", "Customer", "Total", "Outstanding", "Status", "Actions"], rows, "No invoices yet."));

            on(".btn-open", function (id) { V["invoice"](Number(id)); });
            R().content.querySelectorAll(".open-invoice").forEach(function (a) {
                a.addEventListener("click", function (e) { e.preventDefault(); V["invoice"](Number(a.getAttribute("data-id"))); });
            });
            on(".btn-edit", function (id) { V["invoices"](Number(id)); window.scrollTo({ top: 0, behavior: "smooth" }); });

            if (!canWrite()) return;
            const linesEl = document.getElementById("lines");
            const rate = Number(settings.taxRatePercent || 0);
            function recalc() {
                let sub = 0;
                linesEl.querySelectorAll(".invoice-line").forEach(function (row) {
                    sub += (Number(row.querySelector(".l-qty").value) || 0) * (Number(row.querySelector(".l-price").value) || 0);
                });
                const tax = Math.round(sub * rate) / 100;
                document.getElementById("invoiceTotals").innerHTML = "Subtotal <strong>" + money(sub) + "</strong> &nbsp; Tax (" + rate +
                    "%) <strong>" + money(tax) + "</strong> &nbsp; Total <strong>" + money(sub + tax) + "</strong>";
            }
            function addLine(l) {
                const wrap = document.createElement("div");
                wrap.innerHTML = lineRowHtml(l);
                const row = wrap.firstChild;
                row.querySelector(".l-remove").onclick = function () {
                    if (linesEl.querySelectorAll(".invoice-line").length > 1) { row.remove(); recalc(); }
                };
                row.querySelectorAll("input").forEach(function (i) { i.addEventListener("input", recalc); });
                linesEl.appendChild(row);
            }
            ((editing && editing.lines.length) ? editing.lines : [null]).forEach(addLine);
            recalc();
            document.getElementById("addLine").onclick = function () { addLine(); };
            const cancel = document.getElementById("cancelEdit");
            if (cancel) cancel.onclick = function () { V["invoices"](); };

            document.getElementById("invoiceForm").onsubmit = async function (e) {
                e.preventDefault();
                const lines = [];
                let bad = null;
                linesEl.querySelectorAll(".invoice-line").forEach(function (row) {
                    const d = row.querySelector(".l-desc").value.trim(), q = Number(row.querySelector(".l-qty").value),
                        p = row.querySelector(".l-price").value;
                    if (!d || !(q >= 1) || p === "" || Number(p) < 0) bad = "Every line needs a description, a quantity of at least 1 and a unit price.";
                    lines.push({ description: d, quantity: q, unitPrice: Number(p) });
                });
                if (!val("customerName")) bad = "Customer name is required.";
                if (val("dueDate") && val("invoiceDate") && val("dueDate") < val("invoiceDate")) bad = "Due date cannot be before the invoice date.";
                if (bad) { showMsg("formMessage", "error", bad); return; }
                const body = { customerName: val("customerName"), customerEmail: val("customerEmail") || null,
                    customerAddress: val("customerAddress") || null, invoiceDate: val("invoiceDate") || null,
                    dueDate: val("dueDate") || null, notes: val("notes") || null, lines: lines };
                await act("formMessage", async function () {
                    const res = inv
                        ? await R().api("/api/invoices/" + inv.invoiceId, { method: "PUT", body: JSON.stringify(body) })
                        : await post("/api/invoices", body);
                    V["invoice"](res.data.invoice.invoiceId);
                });
            };
        } catch (err) { fail(err); }
    };

    V["invoice"] = async function (id) {
        loading("Invoice");
        try {
            const d = ((await R().api("/api/invoices/" + id)) || {}).data;
            const inv = d.invoice;
            R().pageTitle.textContent = "Invoice " + inv.invoiceNumber;
            let lineRows = "";
            d.lines.forEach(function (l) {
                lineRows += "<tr><td>" + l.lineNo + "</td><td>" + h(l.description) + "</td><td>" + l.quantity + "</td><td>" +
                    money(l.unitPrice) + "</td><td>" + money(l.lineTotal) + "</td></tr>";
            });
            let payRows = "";
            d.payments.forEach(function (p) {
                payRows += "<tr><td>" + h(String(p.paymentDate).substring(0, 10)) + "</td><td>" + h(p.paymentMethod) + "</td><td>" +
                    h(p.referenceNumber) + "</td><td>" + money(p.amount) + "</td></tr>";
            });
            const canPay = canWrite() && (inv.status === "ISSUED" || inv.status === "PARTIALLY_PAID");
            let actions = '<a class="btn btn-secondary" target="_blank" href="/admin/invoice-print.html?id=' + inv.invoiceId + '"><i class="fas fa-print"></i> Print</a> ';
            if (canWrite() && inv.status === "DRAFT") {
                actions += '<button type="button" class="btn btn-secondary" id="editInv"><i class="fas fa-edit"></i> Edit</button> ' +
                    '<button type="button" class="btn btn-primary" id="issueInv"><i class="fas fa-paper-plane"></i> Issue</button> ';
            }
            if (canWrite() && (inv.status === "DRAFT" || (inv.status === "ISSUED" && Number(inv.amountPaid) === 0))) {
                actions += '<button type="button" class="btn btn-danger" id="cancelInv"><i class="fas fa-ban"></i> Cancel invoice</button>';
            }
            R().content.innerHTML = '<div id="formMessage"></div>' +
                '<div class="form-actions" style="margin-bottom:1rem"><button type="button" class="btn btn-secondary" id="backInv"><i class="fas fa-arrow-left"></i> All invoices</button> ' + actions + "</div>" +
                '<div class="stats-grid">' + stat("Status", badge(inv.status)) + stat("Total", money(inv.totalAmount)) +
                stat("Paid", money(inv.amountPaid)) + stat("Outstanding", money(inv.outstanding)) + "</div>" +
                card("Bill To", "<p><strong>" + h(inv.customerName) + "</strong><br>" + h(inv.customerAddress || "") + "<br>" + h(inv.customerEmail || "") +
                    "</p><p class=\"muted\">Date " + h(inv.invoiceDate) + (inv.dueDate ? ", due " + h(inv.dueDate) : "") +
                    ". Tax " + inv.taxRate + "%, currency " + h(inv.currency) + (inv.cancelReason ? ". Cancelled: " + h(inv.cancelReason) : "") + "</p>") +
                card("Line Items", table(["#", "Description", "Qty", "Unit price", "Line total"], lineRows) +
                    '<p class="card-desc">Subtotal ' + money(inv.amount) + ", tax " + money(inv.taxAmount) + ", <strong>total " + money(inv.totalAmount) + "</strong></p>") +
                card("Payments", table(["Date", "Method", "Reference", "Amount"], payRows, "No payments recorded.") +
                    (canPay ? '<form id="payForm" novalidate><div class="form-grid">' +
                        field("Amount", '<input type="number" id="payAmount" min="0.01" step="0.01" max="' + inv.outstanding + '" value="' + inv.outstanding + '">', { required: true }) +
                        field("Date", '<input type="date" id="payDate" max="' + R().todayISO() + '" value="' + R().todayISO() + '">', { required: true }) +
                        field("Method", '<select id="payMethod">' + options(["BANK_TRANSFER", "CASH", "CARD", "CHEQUE"], "BANK_TRANSFER") + "</select>", { required: true }) +
                        field("Reference", '<input type="text" id="payRef" maxlength="100" placeholder="Bank / cheque reference">', { required: true }) +
                        '</div><div class="form-actions"><button type="submit" class="btn btn-primary"><i class="fas fa-money-bill"></i> Record Payment</button></div></form>' : ""),
                    canPay ? "A payment cannot exceed the outstanding balance, and each reference can be used only once." : null);

            document.getElementById("backInv").onclick = function () { V["invoices"](); };
            const reload = function () { V["invoice"](id); };
            const e1 = document.getElementById("editInv"); if (e1) e1.onclick = function () { V["invoices"](id); };
            const e2 = document.getElementById("issueInv");
            if (e2) e2.onclick = function () {
                if (window.confirm("Issue " + inv.invoiceNumber + "? It can no longer be edited after this.")) {
                    act("formMessage", function () { return post("/api/invoices/" + id + "/issue"); }, reload);
                }
            };
            const e3 = document.getElementById("cancelInv");
            if (e3) e3.onclick = function () {
                const reason = askReason("cancel this invoice");
                if (reason) act("formMessage", function () { return post("/api/invoices/" + id + "/cancel", { reason: reason }); }, reload);
            };
            const pf = document.getElementById("payForm");
            if (pf) pf.onsubmit = function (e) {
                e.preventDefault();
                const amount = Number(val("payAmount"));
                if (!(amount > 0)) { R().setFieldError("payAmount", "Enter an amount greater than 0."); return; }
                if (amount > Number(inv.outstanding)) { R().setFieldError("payAmount", "Cannot exceed the outstanding " + money(inv.outstanding) + "."); return; }
                if (!val("payRef")) { R().setFieldError("payRef", "Reference is required."); return; }
                act("formMessage", function () {
                    return post("/api/invoices/" + id + "/payments", { amount: amount, paymentDate: val("payDate"), paymentMethod: val("payMethod"), reference: val("payRef") });
                }, reload);
            };
        } catch (err) { fail(err); }
    };

    V["payments"] = async function () {
        loading("Payments");
        try {
            const [payRes, invRes] = await Promise.all([R().api("/api/payments"), R().api("/api/invoices")]);
            const invNo = {};
            (invRes.data || []).forEach(function (i) { invNo[i.invoiceId] = i.invoiceNumber; });
            let rows = "";
            (payRes.data || []).forEach(function (p) {
                rows += "<tr><td>" + h(String(p.paymentDate).substring(0, 10)) + "</td><td>" + h(p.referenceNumber) + "</td><td>" +
                    (p.invoiceId ? '<a href="#" class="open-invoice" data-id="' + p.invoiceId + '">' + h(invNo[p.invoiceId] || "#" + p.invoiceId) + "</a>" : h(p.paymentType)) +
                    "</td><td>" + h(p.paymentMethod || "-") + "</td><td><strong>" + money(p.amount) + "</strong></td><td>" + badge(p.status) + "</td></tr>";
            });
            R().content.innerHTML = card("Payments Received", table(["Date", "Reference", "Invoice", "Method", "Amount", "Status"], rows, "No payments recorded yet."),
                "Payments are recorded from an issued invoice (open the invoice, then Record Payment).");
            R().content.querySelectorAll(".open-invoice").forEach(function (a) {
                a.addEventListener("click", function (e) { e.preventDefault(); V["invoice"](Number(a.getAttribute("data-id"))); });
            });
        } catch (err) { fail(err); }
    };

    // ===================== FINANCIAL REPORTS (US39, US40) =====================

    V["reports"] = async function () {
        loading("Financial Reports");
        try {
            const items = ((await R().api("/api/reports")) || {}).data || [];
            let rows = "";
            items.forEach(function (r) {
                rows += "<tr><td>#" + r.reportId + "</td><td>" + h(r.reportType.replace(/_/g, " ")) + "</td><td>" + h(r.periodStart) + " to " + h(r.periodEnd) +
                    "</td><td>" + h(String(r.generatedDate).replace("T", " ").substring(0, 16)) + "</td><td>" + badge(r.status) +
                    '</td><td class="actions-cell">' + iconBtn("btn-open", "Open", "fa-folder-open", 'data-id="' + r.reportId + '"') +
                    iconBtn("btn-download", "Download CSV", "fa-download", 'data-id="' + r.reportId + '"') + "</td></tr>";
            });
            const form = canWrite() ? card("Generate Report",
                '<form id="reportForm" novalidate><div class="form-grid">' +
                field("Type", '<select id="reportType">' + options([{ value: "PROFIT_AND_LOSS", label: "Profit and loss" },
                    { value: "REVENUE", label: "Revenue" }, { value: "EXPENSE", label: "Expense" }], "PROFIT_AND_LOSS") + "</select>", { required: true }) +
                field("From", '<input type="date" id="from" value="' + R().monthStartISO(11) + '">', { required: true }) +
                field("To", '<input type="date" id="to" value="' + R().todayISO() + '">', { required: true }) +
                '</div><div class="form-actions"><button type="submit" class="btn btn-primary"><i class="fas fa-cogs"></i> Generate</button></div></form>',
                "The figures are stored when the report is generated, so it re-opens exactly as it was. Finalized reports are read-only.") : "";
            R().content.innerHTML = '<div id="formMessage"></div>' + readOnlyNote() + form +
                card("All Reports", '<div id="listMessage"></div>' + table(["ID", "Type", "Period", "Generated", "Status", "Actions"], rows, "No reports generated yet."));
            on(".btn-open", function (id) { V["report"](Number(id)); });
            on(".btn-download", function (id) { act("listMessage", function () { return downloadWithAuth("/api/reports/" + id + "/download", "report-" + id + ".csv"); }); });
            if (canWrite()) {
                R().linkDateRange("from", "to", { maxToday: true });
                document.getElementById("reportForm").onsubmit = function (e) {
                    e.preventDefault();
                    const err = R().validateFilterRange(val("from"), val("to"));
                    if (err) { showMsg("formMessage", "error", err); return; }
                    act("formMessage", async function () {
                        const res = await post("/api/reports/generate?reportType=" + val("reportType") + "&periodStart=" + val("from") + "&periodEnd=" + val("to"));
                        V["report"](res.data.report.reportId);
                    });
                };
            }
        } catch (err) { fail(err); }
    };

    function figureRows(obj, prefix) {
        let rows = "";
        Object.keys(obj || {}).forEach(function (k) {
            const v = obj[k], label = (prefix ? prefix + " / " : "") + k.replace(/([A-Z])/g, " $1").replace(/^./, function (c) { return c.toUpperCase(); });
            if (v && typeof v === "object") rows += figureRows(v, label);
            else rows += "<tr><td>" + h(label) + "</td><td>" + (typeof v === "number" && !/count|sales|sold/i.test(k) ? money(v) : h(v)) + "</td></tr>";
        });
        return rows;
    }

    V["report"] = async function (id) {
        loading("Report");
        try {
            const d = ((await R().api("/api/reports/" + id)) || {}).data;
            const r = d.report, f = d.figures || {};
            R().pageTitle.textContent = r.reportType.replace(/_/g, " ") + " report #" + r.reportId;
            const finalized = r.status === "FINALIZED";
            let buttons = '<button type="button" class="btn btn-secondary" id="backRep"><i class="fas fa-arrow-left"></i> All reports</button> ' +
                '<button type="button" class="btn btn-secondary" id="dlRep"><i class="fas fa-download"></i> Download CSV</button> ' +
                '<button type="button" class="btn btn-secondary" onclick="window.print()"><i class="fas fa-print"></i> Print</button> ';
            if (canWrite() && !finalized) {
                buttons += '<button type="button" class="btn btn-secondary" id="regenRep"><i class="fas fa-sync"></i> Regenerate</button> ' +
                    (r.status === "GENERATED" ? '<button type="button" class="btn btn-secondary" id="reviewRep"><i class="fas fa-eye"></i> Mark reviewed</button> ' : "") +
                    '<button type="button" class="btn btn-primary" id="finalRep"><i class="fas fa-lock"></i> Finalize</button> ' +
                    '<button type="button" class="btn btn-danger" id="delRep"><i class="fas fa-trash"></i> Delete</button>';
            }
            let headline = "";
            if (r.reportType === "PROFIT_AND_LOSS") {
                headline = '<div class="stats-grid">' + stat("Net Revenue", money(f.netRevenue)) + stat("Operating Expenses", money(f.operatingExpenses)) +
                    stat("Royalties Paid", money(f.royaltiesPaid)) + stat("Profit", money(f.profit)) + "</div>";
            } else if (r.reportType === "REVENUE") {
                headline = '<div class="stats-grid">' + stat("Net Revenue", money(f.netRevenue)) + stat("Gross Revenue", money(f.grossRevenue)) +
                    stat("Completed Sales", f.completedSales) + stat("Books Sold", f.booksSold) + "</div>";
            } else {
                headline = '<div class="stats-grid">' + stat("Total Expenses", money(f.totalExpenses)) + stat("Expenses Counted", f.expenseCount) + "</div>";
            }
            R().content.innerHTML = '<div id="formMessage"></div><div class="form-actions no-print" style="margin-bottom:1rem">' + buttons + "</div>" +
                card(null, "<p><strong>Period:</strong> " + h(r.periodStart) + " to " + h(r.periodEnd) + " &nbsp; <strong>Status:</strong> " + badge(r.status) +
                    " &nbsp; <strong>Currency:</strong> " + h(r.currency || "") + "<br><span class=\"muted\">Generated " +
                    h(String(r.generatedDate).replace("T", " ").substring(0, 16)) +
                    (finalized ? ", finalized " + h(String(r.finalizedAt).replace("T", " ").substring(0, 16)) + " (read-only)" : "") + "</span></p>") +
                headline + card("Figures", table(["Item", "Value"], figureRows(f)));

            const reload = function () { V["report"](id); };
            document.getElementById("backRep").onclick = function () { V["reports"](); };
            document.getElementById("dlRep").onclick = function () { act("formMessage", function () { return downloadWithAuth("/api/reports/" + id + "/download", "report-" + id + ".csv"); }); };
            const bind = function (btnId, fn) { const b = document.getElementById(btnId); if (b) b.onclick = fn; };
            bind("regenRep", function () { act("formMessage", function () { return post("/api/reports/" + id + "/regenerate"); }, reload); });
            bind("reviewRep", function () { act("formMessage", function () { return post("/api/reports/" + id + "/review"); }, reload); });
            bind("finalRep", function () {
                if (window.confirm("Finalize this report? It becomes read-only and cannot be changed or deleted.")) {
                    act("formMessage", function () { return post("/api/reports/" + id + "/finalize"); }, reload);
                }
            });
            bind("delRep", function () {
                if (window.confirm("Delete this report?")) act("formMessage", function () { return R().api("/api/reports/" + id, { method: "DELETE" }); }, function () { V["reports"](); });
            });
        } catch (err) { fail(err); }
    };

    // ===================== ROYALTY AGREEMENTS (US41) =====================

    V["royalty-agreements"] = async function (editId) {
        loading("Royalty Agreements");
        try {
            const [agrRes, authRes, bookRes] = await Promise.all([R().api("/api/royalty-agreements"),
                R().api("/api/royalty-agreements/lookup/authors"), R().api("/api/royalty-agreements/lookup/books")]);
            const items = agrRes.data || [], authors = authRes.data || [], books = bookRes.data || [];
            const authorName = {}, bookTitle = {};
            authors.forEach(function (a) { authorName[a.authorId] = a.fullName; });
            books.forEach(function (b) { bookTitle[b.bookId] = b.title; });
            const editing = editId ? items.find(function (a) { return a.royaltyAgreementId === editId; }) : null;

            let rows = "";
            items.forEach(function (a) {
                let act2 = "";
                if (canWrite() && a.status === "DRAFT") {
                    act2 = iconBtn("btn-edit", "Edit", "fa-edit", 'data-id="' + a.royaltyAgreementId + '"') +
                        iconBtn("btn-activate", "Activate", "fa-play", 'data-id="' + a.royaltyAgreementId + '"');
                }
                if (canWrite() && a.status !== "EXPIRED") act2 += iconBtn("btn-expire", "Expire", "fa-ban", 'data-id="' + a.royaltyAgreementId + '"');
                rows += "<tr><td>" + h(a.agreementNumber) + "</td><td>" + h(authorName[a.authorId] || "Author #" + a.authorId) + "</td><td>" +
                    h(bookTitle[a.bookId] || "Book #" + a.bookId) + "</td><td>" + a.royaltyPercentage + "%" +
                    (a.wholesaleRoyaltyPercentage != null ? " / " + a.wholesaleRoyaltyPercentage + "% wholesale" : "") + "</td><td>" +
                    h((a.basis || "").replace("_", " ")) + "</td><td>" + money(a.advanceAmount) +
                    (Number(a.advanceAmount) > 0 ? '<div class="muted">recouped ' + money(a.advanceRecouped) + "</div>" : "") + "</td><td>" +
                    h(a.effectiveDate) + " to " + h(a.expiryDate || "open") + "</td><td>" + h(a.paymentFrequency) + "</td><td>" + badge(a.status) +
                    '</td><td class="actions-cell">' + (act2 || '<span class="muted">-</span>') + "</td></tr>";
            });

            const authorOpts = authors.map(function (a) { return { value: a.authorId, label: a.fullName + " (#" + a.authorId + ")" }; });
            const form = canWrite() ? card(editing ? "Edit Draft " + h(editing.agreementNumber) : "New Royalty Agreement",
                (authors.length ? "" : alertHtml("error", "No authors found in the author directory (Epic 1). Agreements need an existing author and book.")) +
                '<form id="agreementForm" novalidate><div class="form-grid">' +
                field("Author", '<select id="authorId">' + options(authorOpts, editing && editing.authorId, "Select an author") + "</select>", { required: true }) +
                field("Book", '<select id="bookId"><option value="">Select the author first</option></select>', { required: true, hint: "Only the selected author's books are listed." }) +
                field("Royalty %", '<input type="number" id="royaltyPercentage" min="0.01" max="50" step="0.01" value="' + (editing ? editing.royaltyPercentage : "") + '">', { required: true, hint: "0.01 to 50" }) +
                field("Wholesale royalty %", '<input type="number" id="wholesaleRoyaltyPercentage" min="0" max="50" step="0.01" value="' + (editing && editing.wholesaleRoyaltyPercentage != null ? editing.wholesaleRoyaltyPercentage : "") + '">', { hint: "Bookstore sales. Empty = same as royalty %." }) +
                field("Basis", '<select id="basis">' + options([{ value: "NET_SALES", label: "Net sales (price less discount)" }, { value: "LIST_PRICE", label: "List price" }], editing ? editing.basis : "NET_SALES") + "</select>", { required: true }) +
                field("Advance", '<input type="number" id="advanceAmount" min="0" step="0.01" value="' + (editing ? editing.advanceAmount : 0) + '">', { hint: "Recouped from royalties before anything is paid." }) +
                field("Payment frequency", '<select id="paymentFrequency">' + options(["QUARTERLY", "BIANNUAL", "ANNUAL"], editing ? editing.paymentFrequency : "QUARTERLY") + "</select>") +
                field("Effective from", '<input type="date" id="effectiveDate" value="' + (editing ? editing.effectiveDate : "") + '">', { required: true }) +
                field("Expiry (optional)", '<input type="date" id="expiryDate" value="' + (editing && editing.expiryDate ? editing.expiryDate : "") + '">') +
                '</div><div class="form-actions"><button type="submit" class="btn btn-primary"><i class="fas ' + (editing ? "fa-save" : "fa-plus") + '"></i> ' +
                (editing ? "Save Draft" : "Create Draft") + "</button>" + (editing ? '<button type="button" class="btn btn-secondary" id="cancelEdit">Cancel</button>' : "") +
                "</div></form>", "Created as DRAFT, then activated. Two ACTIVE agreements for the same book cannot overlap in dates.") : "";

            R().content.innerHTML = '<div id="formMessage"></div>' + readOnlyNote() + form +
                card("All Agreements (" + items.length + ")", '<div id="listMessage"></div>' +
                    table(["Number", "Author", "Book", "Rate", "Basis", "Advance", "Dates", "Frequency", "Status", "Actions"], rows, "No royalty agreements yet."));

            const reload = function () { V["royalty-agreements"](); };
            on(".btn-edit", function (id) { V["royalty-agreements"](Number(id)); window.scrollTo({ top: 0, behavior: "smooth" }); });
            on(".btn-activate", function (id) { act("listMessage", function () { return post("/api/royalty-agreements/" + id + "/activate"); }, reload); });
            on(".btn-expire", function (id) {
                if (window.confirm("Expire this agreement? No further royalties can be calculated on it.")) {
                    act("listMessage", function () { return post("/api/royalty-agreements/" + id + "/expire"); }, reload);
                }
            });
            if (!canWrite()) return;

            const bookSel = document.getElementById("bookId");
            function fillBooks(selected) {
                const aid = val("authorId");
                const mine = books.filter(function (b) { return String(b.authorId) === aid; });
                bookSel.innerHTML = aid ? options(mine.map(function (b) { return { value: b.bookId, label: b.title + " (#" + b.bookId + ")" }; }), selected,
                    mine.length ? "Select a book" : "This author has no books in the catalogue") : '<option value="">Select the author first</option>';
            }
            document.getElementById("authorId").onchange = function () { fillBooks(); };
            fillBooks(editing && editing.bookId);
            R().linkDateRange("effectiveDate", "expiryDate", {});
            const cancel = document.getElementById("cancelEdit");
            if (cancel) cancel.onclick = reload;

            document.getElementById("agreementForm").onsubmit = function (e) {
                e.preventDefault();
                const pct = Number(val("royaltyPercentage")), wpct = val("wholesaleRoyaltyPercentage");
                let err = null;
                if (!val("authorId") || !val("bookId") || !val("royaltyPercentage") || !val("effectiveDate")) err = "Author, book, royalty % and effective date are required.";
                else if (!(pct > 0 && pct <= 50)) err = "Royalty % must be between 0.01 and 50.";
                else if (wpct && !(Number(wpct) >= 0 && Number(wpct) <= 50)) err = "Wholesale royalty % must be between 0 and 50.";
                else if (Number(val("advanceAmount") || 0) < 0) err = "Advance cannot be negative.";
                else err = R().validateAgreementDates(val("effectiveDate"), val("expiryDate"));
                if (err) { showMsg("formMessage", "error", err); return; }
                const body = { authorId: Number(val("authorId")), bookId: Number(val("bookId")), royaltyPercentage: pct,
                    wholesaleRoyaltyPercentage: wpct ? Number(wpct) : null, basis: val("basis"),
                    advanceAmount: Number(val("advanceAmount") || 0), paymentFrequency: val("paymentFrequency"),
                    effectiveDate: val("effectiveDate"), expiryDate: val("expiryDate") || null };
                act("formMessage", function () {
                    return editing ? R().api("/api/royalty-agreements/" + editing.royaltyAgreementId, { method: "PUT", body: JSON.stringify(body) })
                        : post("/api/royalty-agreements", body);
                }, reload);
            };
        } catch (err) { fail(err); }
    };

    // ===================== ROYALTY CALCULATIONS (US42, US43) =====================

    function calcSummaryHtml(c, unitsByChannel) {
        const units = Object.keys(unitsByChannel || {}).map(function (k) { return h(k) + " " + unitsByChannel[k]; }).join(", ");
        return '<div class="stats-grid">' + stat("Books Sold", c.booksSold + (units ? '<div class="muted small">' + units + "</div>" : "")) +
            stat("Net Sales", money(c.grossSales)) + stat("Gross Royalty", money(c.royaltyAmount)) + stat("Payable", money(c.payableAmount)) + "</div>" +
            '<p class="card-desc">Royalty base ' + money(c.royaltyBase) + (Number(c.deductions) > 0 ? " (after deductions " + money(c.deductions) + ")" : "") +
            ", advance recouped " + money(c.advanceRecouped) + (Number(c.carriedForwardIn) > 0 ? ", carried forward in " + money(c.carriedForwardIn) : "") +
            ", returned units " + c.unitsReturned + " (" + money(c.returnsAmount) + ", no royalty).</p>";
    }

    function linesTable(lines) {
        let rows = "";
        (lines || []).forEach(function (l) {
            rows += "<tr" + (l.lineType === "RETURN" ? ' class="muted"' : "") + "><td>" + h(l.saleDate) + "</td><td>" + h(l.saleReference || "") + "</td><td>" + h(l.channel) +
                "</td><td>" + h(l.lineType) + "</td><td>" + l.quantity + "</td><td>" + money(l.unitPrice) + "</td><td>" + money(l.discount) + "</td><td>" +
                money(l.baseAmount) + "</td><td>" + l.rateApplied + "%</td><td><strong>" + money(l.royaltyAmount) + "</strong></td></tr>";
        });
        return table(["Date", "Sale", "Channel", "Type", "Qty", "Unit price", "Discount", "Base", "Rate", "Royalty"], rows, "No lines.");
    }

    V["royalty-calculations"] = async function (statusFilter) {
        loading("Royalty Calculations");
        try {
            const [agrRes, calcRes] = await Promise.all([R().api("/api/royalty-agreements"),
                R().api("/api/royalties" + (statusFilter ? "?status=" + statusFilter : ""))]);
            const agreements = (agrRes.data || []).filter(function (a) { return a.status === "ACTIVE"; });
            const calcs = calcRes.data || [];
            const agreementById = {};
            agreements.forEach(function (a) { agreementById[a.royaltyAgreementId] = a; });

            let rows = "";
            calcs.forEach(function (s) {
                const c = s.calculation;
                let a2 = iconBtn("btn-open", "Open", "fa-folder-open", 'data-id="' + c.calculationId + '"');
                if (c.statementNumber && ["STATEMENT_ISSUED", "APPROVED", "CARRIED_FORWARD", "PAID"].indexOf(c.status) >= 0) {
                    a2 += '<a class="btn-icon" title="Statement" target="_blank" href="/admin/royalty-statement.html?id=' + c.calculationId + '"><i class="fas fa-file-invoice-dollar"></i></a> ';
                }
                rows += "<tr><td>#" + c.calculationId + "</td><td>" + h(s.agreementNumber) + "<div class=\"muted\">" + h(s.authorName) + ", " + h(s.bookTitle) +
                    "</div></td><td>" + h(c.salesPeriodStart) + " to " + h(c.salesPeriodEnd) + "</td><td>" + c.booksSold + "</td><td>" + money(c.royaltyAmount) +
                    "</td><td><strong>" + money(c.payableAmount) + "</strong></td><td>" + badge(c.status) + '</td><td class="actions-cell">' + a2 + "</td></tr>";
            });

            let opts = '<option value="">Select an active agreement</option>';
            agreements.forEach(function (a) {
                opts += '<option value="' + a.royaltyAgreementId + '">' + h(a.agreementNumber) + " (Author #" + a.authorId + ", Book #" + a.bookId + ", " + a.royaltyPercentage + "%)</option>";
            });

            const form = canWrite() ? card("Calculate Royalty",
                (agreements.length ? "" : alertHtml("error", "No ACTIVE royalty agreements. Activate one on the Royalty Agreements page first.")) +
                '<form id="calcForm" novalidate><div class="form-grid">' +
                field("Agreement", '<select id="agreementId">' + opts + "</select>", { required: true, full: true }) +
                field("Period start", '<input type="date" id="periodStart" value="' + R().monthStartISO(3) + '">', { required: true }) +
                field("Period end", '<input type="date" id="periodEnd" value="' + R().lastDayOfPrevMonthISO() + '">', { required: true }) +
                field("Deductions", '<input type="number" id="deductions" min="0" step="0.01" value="0">', { hint: "Optional amount taken off the royalty base." }) +
                '</div><div class="form-actions"><button type="button" class="btn btn-secondary" id="previewBtn"><i class="fas fa-search"></i> Preview</button>' +
                '<button type="submit" class="btn btn-primary" id="saveBtn" disabled><i class="fas fa-calculator"></i> Save Calculation</button></div></form>' +
                '<div id="previewBox"></div>',
                "Preview first: the royalty is worked out line by line from the <strong>completed</strong> sales of the agreement's book. " +
                "Save is enabled once the preview succeeds. A period that overlaps an existing calculation for the same agreement is rejected.") : "";

            const filterBar = '<div class="form-actions" style="margin-bottom:.5rem">' + ["", "CALCULATED", "STATEMENT_ISSUED", "APPROVED", "CARRIED_FORWARD", "PAID", "CANCELLED"].map(function (st) {
                return '<button type="button" class="btn ' + ((statusFilter || "") === st ? "btn-primary" : "btn-secondary") + ' status-filter" data-status="' + st + '">' + (st ? st.replace(/_/g, " ") : "All") + "</button>";
            }).join(" ") + "</div>";

            R().content.innerHTML = '<div id="formMessage"></div>' + readOnlyNote() + form +
                card("Calculations", filterBar + '<div id="listMessage"></div>' +
                    table(["ID", "Agreement", "Period", "Books Sold", "Gross Royalty", "Payable", "Status", "Actions"], rows, "No calculations yet."));

            R().content.querySelectorAll(".status-filter").forEach(function (b) {
                b.onclick = function () { V["royalty-calculations"](b.getAttribute("data-status") || null); };
            });
            on(".btn-open", function (id) { V["royalty-calculation"](Number(id)); });
            if (!canWrite()) return;

            R().linkDateRange("periodStart", "periodEnd", { maxToday: true });
            const saveBtn = document.getElementById("saveBtn");
            function body() {
                return { royaltyAgreementId: Number(val("agreementId")), periodStart: val("periodStart"), periodEnd: val("periodEnd"), deductions: Number(val("deductions") || 0) };
            }
            function check() {
                if (!val("agreementId") || !val("periodStart") || !val("periodEnd")) return "Choose an agreement and both period dates.";
                if (Number(val("deductions") || 0) < 0) return "Deductions cannot be negative.";
                return R().validateRoyaltyPeriod(val("periodStart"), val("periodEnd"), agreementById[val("agreementId")]);
            }
            ["agreementId", "periodStart", "periodEnd", "deductions"].forEach(function (id) {
                document.getElementById(id).addEventListener("change", function () { saveBtn.disabled = true; document.getElementById("previewBox").innerHTML = ""; });
            });
            document.getElementById("previewBtn").onclick = async function () {
                const err = check();
                if (err) { showMsg("formMessage", "error", err); return; }
                showMsg("formMessage", null, null);
                const box = document.getElementById("previewBox");
                box.innerHTML = '<p class="muted"><i class="fas fa-spinner fa-spin"></i> Calculating preview...</p>';
                try {
                    const p = (await post("/api/royalties/preview", body())).data;
                    box.innerHTML = "<h3 class=\"sub-heading\">Preview (not saved): " + h(p.authorName) + ", " + h(p.bookTitle) + "</h3>" +
                        calcSummaryHtml(p.calculation, p.unitsByChannel) + linesTable(p.lines);
                    saveBtn.disabled = false;
                } catch (e2) {
                    box.innerHTML = alertHtml("error", e2.message);
                }
            };
            document.getElementById("calcForm").onsubmit = function (e) {
                e.preventDefault();
                const err = check();
                if (err) { showMsg("formMessage", "error", err); return; }
                act("formMessage", async function () {
                    const res = await post("/api/royalties/calculate", body());
                    V["royalty-calculation"](res.data.calculationId);
                });
            };
        } catch (err) { fail(err); }
    };

    V["royalty-calculation"] = async function (id) {
        loading("Royalty Calculation");
        try {
            const d = ((await R().api("/api/royalties/" + id)) || {}).data;
            const c = d.calculation, a = d.agreement;
            R().pageTitle.textContent = "Royalty Calculation #" + c.calculationId;
            let buttons = '<button type="button" class="btn btn-secondary" id="backCalc"><i class="fas fa-arrow-left"></i> All calculations</button> ';
            if (c.statementNumber && ["STATEMENT_ISSUED", "APPROVED", "CARRIED_FORWARD", "PAID"].indexOf(c.status) >= 0) {
                buttons += '<a class="btn btn-secondary" target="_blank" href="/admin/royalty-statement.html?id=' + c.calculationId + '"><i class="fas fa-file-invoice-dollar"></i> Statement</a> ';
            }
            if (canWrite()) {
                if (c.status === "CALCULATED") {
                    buttons += '<button type="button" class="btn btn-primary" id="issueSt"><i class="fas fa-file-signature"></i> Issue Statement</button> ' +
                        '<button type="button" class="btn btn-secondary" id="recalc"><i class="fas fa-sync"></i> Recalculate</button> ' +
                        '<button type="button" class="btn btn-danger" id="cancelCalc"><i class="fas fa-ban"></i> Cancel</button> ';
                }
                if (c.status === "STATEMENT_ISSUED") {
                    buttons += '<button type="button" class="btn btn-primary" id="approveCalc"><i class="fas fa-check"></i> Approve</button> ' +
                        '<button type="button" class="btn btn-danger" id="rejectCalc"><i class="fas fa-times"></i> Reject</button> ';
                }
                if (c.status === "APPROVED") buttons += '<button type="button" class="btn btn-primary" id="payCalc"><i class="fas fa-hand-holding-usd"></i> Record Payment</button> ';
            }
            const notes = [];
            if (c.statementNumber) notes.push("Statement " + h(c.statementNumber));
            if (c.rejectionReason) notes.push("Last rejection: " + h(c.rejectionReason));
            if (c.cancelReason) notes.push("Cancelled: " + h(c.cancelReason));
            if (c.status === "CARRIED_FORWARD") notes.push(c.carriedIntoCalculationId ? "Carried into calculation #" + c.carriedIntoCalculationId : "Below the payment threshold, will be added to the next calculation");

            R().content.innerHTML = '<div id="formMessage"></div><div class="form-actions" style="margin-bottom:1rem">' + buttons + "</div>" +
                card(h(d.authorName) + ", " + h(d.bookTitle),
                    "<p>" + badge(c.status) + " &nbsp; Agreement " + h(a.agreementNumber) + ", period " + h(c.salesPeriodStart) + " to " + h(c.salesPeriodEnd) +
                    ", basis " + h(c.basis || a.basis) + ", rate " + c.royaltyRate + "%" + (c.wholesaleRate != null ? " (wholesale " + c.wholesaleRate + "%)" : "") +
                    (notes.length ? '<br><span class="muted">' + notes.join(". ") + "</span>" : "") + "</p>" + calcSummaryHtml(c, d.unitsByChannel)) +
                card("Sale Lines", linesTable(d.lines)) + '<div id="payBox"></div>';

            const reload = function () { V["royalty-calculation"](id); };
            document.getElementById("backCalc").onclick = function () { V["royalty-calculations"](); };
            const bind = function (btnId, fn) { const b = document.getElementById(btnId); if (b) b.onclick = fn; };
            bind("issueSt", function () { act("formMessage", function () { return post("/api/royalties/" + id + "/statement"); }, reload); });
            bind("recalc", function () {
                if (window.confirm("Recalculate? This calculation is cancelled and the same period is calculated again from the current sales.")) {
                    act("formMessage", async function () { const r2 = await post("/api/royalties/" + id + "/recalculate"); V["royalty-calculation"](r2.data.calculationId); });
                }
            });
            bind("cancelCalc", function () {
                const reason = askReason("cancel this calculation");
                if (reason) act("formMessage", function () { return post("/api/royalties/" + id + "/cancel", { reason: reason }); }, reload);
            });
            bind("approveCalc", function () { act("formMessage", function () { return post("/api/royalties/" + id + "/approve"); }, reload); });
            bind("rejectCalc", function () {
                const reason = askReason("reject this statement");
                if (!reason) return;
                const cancel = window.confirm("Also cancel the calculation?\n\nOK: cancel it (frees the period).\nCancel: send it back to CALCULATED for correction.");
                act("formMessage", function () { return post("/api/royalties/" + id + "/reject", { reason: reason, cancel: cancel }); }, reload);
            });
            bind("payCalc", function () { renderPayForm("payBox", c.payableAmount, "/api/royalties/" + id + "/pay", reload); });
        } catch (err) { fail(err); }
    };

    function renderPayForm(boxId, amount, url, after) {
        const box = document.getElementById(boxId);
        box.innerHTML = card("Record Royalty Payment", '<div id="payMsg"></div><form id="royaltyPayForm" novalidate><div class="form-grid">' +
            field("Amount", '<input type="number" id="rpAmount" value="' + amount + '" readonly>', { hint: "Must equal the approved payable amount." }) +
            field("Payment date", '<input type="date" id="rpDate" max="' + R().todayISO() + '" value="' + R().todayISO() + '">', { required: true }) +
            field("Method", '<select id="rpMethod">' + options(["BANK_TRANSFER", "CHEQUE", "CASH", "CARD"], "BANK_TRANSFER") + "</select>", { required: true }) +
            field("Payment reference", '<input type="text" id="rpRef" maxlength="100" placeholder="Bank transfer / cheque number">', { required: true, hint: "Must be unique across all royalty payments." }) +
            '</div><div class="form-actions"><button type="submit" class="btn btn-primary"><i class="fas fa-check"></i> Mark Paid</button></div></form>',
            "Once paid, the record cannot be changed. The payment is also posted as a ROYALTY expense.");
        box.scrollIntoView({ behavior: "smooth" });
        document.getElementById("royaltyPayForm").onsubmit = function (e) {
            e.preventDefault();
            if (!val("rpRef")) { R().setFieldError("rpRef", "Payment reference is required."); return; }
            act("payMsg", function () {
                return post(url, { amount: Number(amount), paymentDate: val("rpDate"), paymentMethod: val("rpMethod"), transactionReference: val("rpRef") });
            }, after);
        };
    }

    // ===================== APPROVALS (US45) & ROYALTY PAYMENTS (US46) =====================

    V["royalty-approvals"] = async function () {
        loading("Statement Approvals");
        try {
            const [calcRes, setRes] = await Promise.all([R().api("/api/royalties?status=STATEMENT_ISSUED"), R().api("/api/settings/public")]);
            let threshold = null;
            try { threshold = ((await R().api("/api/settings")).data || []).find(function (s) { return s.settingKey === "royaltyPaymentThreshold"; }); } catch (ignored) { /* admin-only list */ }
            let rows = "";
            (calcRes.data || []).forEach(function (s) {
                const c = s.calculation;
                rows += "<tr><td>" + h(c.statementNumber) + "</td><td>" + h(s.authorName) + "</td><td>" + h(s.bookTitle) + "</td><td>" + h(c.salesPeriodStart) + " to " +
                    h(c.salesPeriodEnd) + "</td><td>" + money(c.royaltyAmount) + "</td><td><strong>" + money(c.payableAmount) + '</strong></td><td class="actions-cell">' +
                    '<a class="btn-icon" title="Statement" target="_blank" href="/admin/royalty-statement.html?id=' + c.calculationId + '"><i class="fas fa-file-invoice-dollar"></i></a> ' +
                    iconBtn("btn-open", "Open", "fa-folder-open", 'data-id="' + c.calculationId + '"') +
                    (canWrite() ? iconBtn("btn-approve", "Approve", "fa-check", 'data-id="' + c.calculationId + '"') +
                        iconBtn("btn-reject", "Reject", "fa-times", 'data-id="' + c.calculationId + '"') : "") + "</td></tr>";
            });
            R().content.innerHTML = readOnlyNote() + card("Statements Awaiting Approval", '<div id="listMessage"></div>' +
                table(["Statement", "Author", "Book", "Period", "Gross Royalty", "Payable", "Actions"], rows, "No statements are waiting for approval."),
                "Approving creates the payment to be made. A payable amount below the royalty payment threshold" +
                (threshold ? " (" + money(threshold.settingValue) + ")" : "") + " is carried forward to the next period instead." +
                (setRes.data ? " Amounts in " + h(setRes.data.currency) + "." : ""));
            const reload = function () { V["royalty-approvals"](); };
            on(".btn-open", function (id) { V["royalty-calculation"](Number(id)); });
            on(".btn-approve", function (id) { act("listMessage", function () { return post("/api/royalties/" + id + "/approve"); }, reload); });
            on(".btn-reject", function (id) {
                const reason = askReason("reject this statement");
                if (!reason) return;
                const cancel = window.confirm("Also cancel the calculation?\n\nOK: cancel it (frees the period).\nCancel: send it back to CALCULATED for correction.");
                act("listMessage", function () { return post("/api/royalties/" + id + "/reject", { reason: reason, cancel: cancel }); }, reload);
            });
        } catch (err) { fail(err); }
    };

    V["royalty-payments"] = async function () {
        loading("Royalty Payments");
        try {
            const [payRes, calcRes] = await Promise.all([R().api("/api/royalty-payments"), R().api("/api/royalties")]);
            const byCalc = {};
            (calcRes.data || []).forEach(function (s) { byCalc[s.calculation.calculationId] = s; });
            let rows = "";
            (payRes.data || []).forEach(function (p) {
                const s = byCalc[p.calculationId] || {};
                const payable = ["APPROVED", "SCHEDULED", "PROCESSING"].indexOf(p.paymentStatus) >= 0;
                rows += "<tr><td>" + h(p.paymentReference) + "</td><td>" + h(s.authorName || "") + "<div class=\"muted\">" + h(s.bookTitle || "") + "</div></td><td>" +
                    h(s.calculation ? s.calculation.statementNumber || "" : "") + "</td><td><strong>" + money(p.amount) + "</strong></td><td>" + badge(p.paymentStatus) +
                    "</td><td>" + (p.paymentStatus === "PAID" ? h(p.paymentDate) + ", " + h((p.paymentMethod || "").replace("_", " ")) + "<div class=\"muted\">ref " + h(p.transactionReference) + "</div>" : "-") +
                    '</td><td class="actions-cell">' + (canWrite() && payable ? iconBtn("btn-pay", "Record payment", "fa-hand-holding-usd", 'data-id="' + p.royaltyPaymentId + '" data-amount="' + p.amount + '"') : '<span class="muted">-</span>') +
                    "</td></tr>";
            });
            R().content.innerHTML = readOnlyNote() + card("Royalty Payments", '<div id="listMessage"></div>' +
                table(["Payment", "Author / Book", "Statement", "Amount", "Status", "Paid", "Actions"], rows, "No royalty payments yet. Payments appear here when a statement is approved.")) +
                '<div id="payBox"></div>';
            on(".btn-pay", function (id, el) {
                renderPayForm("payBox", el.getAttribute("data-amount"), "/api/royalty-payments/" + id + "/mark-paid", function () { V["royalty-payments"](); });
            });
        } catch (err) { fail(err); }
    };

    // ===================== EXECUTIVE ANALYTICS (US48 - US50) =====================

    let chartLib = null;
    function loadChartJs() {
        if (window.Chart) return Promise.resolve();
        if (chartLib) return chartLib;
        chartLib = new Promise(function (resolve, reject) {
            const s = document.createElement("script");
            s.src = "https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.1/chart.umd.min.js";
            s.onload = resolve;
            s.onerror = function () { chartLib = null; reject(new Error("Could not load the chart library")); };
            document.head.appendChild(s);
        });
        return chartLib;
    }

    const charts = [];
    function chart(canvasId, config) {
        if (!window.Chart) return;
        charts.push(new window.Chart(document.getElementById(canvasId), config));
    }

    V["analytics"] = async function (range) {
        range = range || {};
        const from = range.from || R().monthStartISO(11), to = range.to || R().todayISO();
        loading("Executive Analytics");
        while (charts.length) charts.pop().destroy();
        try {
            const q = "?from=" + from + "&to=" + to;
            const [dash, books, authors, roy] = await Promise.all([R().api("/api/analytics/dashboard" + q), R().api("/api/analytics/books" + q),
                R().api("/api/analytics/authors" + q), R().api("/api/analytics/royalties" + q)]);
            const d = dash.data, k = d.kpis, b = books.data, a = authors.data, r = roy.data;
            try { await loadChartJs(); } catch (ignored) { /* tables still render */ }

            function rankRows(list, nameKey) {
                return (list || []).map(function (x, i) {
                    return "<tr><td>" + (i + 1) + "</td><td>" + h(x[nameKey]) + "</td><td>" + x.units + "</td><td>" + money(x.revenue) + "</td></tr>";
                }).join("");
            }
            let liability = "";
            (r.liabilityByAuthor || []).forEach(function (x) {
                liability += "<tr><td>" + h(x.authorName) + "</td><td>" + money(x.calculated) + "</td><td>" + money(x.statementIssued) + "</td><td>" +
                    money(x.approved) + "</td><td><strong>" + money(x.total) + "</strong></td></tr>";
            });

            R().content.innerHTML = card(null, dateRangeForm("rangeForm", from, to)) +
                '<div class="stats-grid">' + stat("Revenue This Month", money(k.revenueThisMonth)) + stat("Revenue Year to Date", money(k.revenueYearToDate)) +
                stat("Orders (range)", k.orders) + stat("Units Sold (range)", k.unitsSold) + "</div>" +
                '<div class="chart-grid">' +
                card("Revenue by Month", '<div class="chart-box"><canvas id="chRevenue"></canvas></div>') +
                card("Channel Split", '<div class="chart-box"><canvas id="chChannel"></canvas></div>') + "</div>" +
                '<div class="chart-grid">' +
                card("Top Categories", '<div class="chart-box"><canvas id="chCategory"></canvas></div>') +
                card("Royalties: Paid vs Outstanding", '<div class="chart-box"><canvas id="chRoyalty"></canvas></div>' +
                    '<p class="card-desc">Royalty earned ' + money(r.royaltyEarned) + " is <strong>" + r.royaltyPercentOfRevenue + "%</strong> of net revenue " +
                    money(r.netRevenue) + ". Carried forward: " + money(r.carriedForward) + ".</p>") + "</div>" +
                '<div class="chart-grid">' +
                card("Top 10 Books by Units", table(["#", "Book", "Units", "Revenue"], rankRows(b.topByUnits, "bookTitle"), "No sales in this range.")) +
                card("Top 10 Books by Revenue", table(["#", "Book", "Units", "Revenue"], rankRows(b.topByRevenue, "bookTitle"), "No sales in this range.")) + "</div>" +
                card("Top Authors", table(["#", "Author", "Units", "Revenue"], rankRows(a.topAuthors, "authorName"), "No sales could be linked to authors.") +
                    (a.unattributedUnits ? '<p class="card-desc">' + a.unattributedUnits + " units could not be linked to an author yet.</p>" : "")) +
                card("Royalty Liability by Author", table(["Author", "Calculated", "Statement issued", "Approved", "Total outstanding"], liability, "Nothing outstanding."),
                    "Outstanding = calculated, statement issued and approved royalties that are not paid yet.") +
                card("Book Trend", '<div class="form-grid">' + field("Book", '<select id="trendBook">' + options((b.topByRevenue || []).map(function (x) {
                    return { value: x.bookId, label: x.bookTitle };
                }), null, "Select a book") + "</select>") + '</div><div class="chart-box"><canvas id="chTrend"></canvas></div>');

            const months = (d.revenueByMonth || []).map(function (m) { return m.month; });
            chart("chRevenue", { type: "line", data: { labels: months, datasets: [{ label: "Net revenue", data: d.revenueByMonth.map(function (m) { return m.revenue; }), borderColor: "#2b6cb0", backgroundColor: "rgba(43,108,176,.15)", fill: true, tension: .25 }] },
                options: { maintainAspectRatio: false, plugins: { legend: { display: false } } } });
            const ch = d.revenueByChannel || {};
            chart("chChannel", { type: "doughnut", data: { labels: Object.keys(ch), datasets: [{ data: Object.values(ch), backgroundColor: ["#2b6cb0", "#dd6b20", "#38a169"] }] },
                options: { maintainAspectRatio: false } });
            chart("chCategory", { type: "bar", data: { labels: (d.topCategories || []).map(function (c) { return c.category; }),
                datasets: [{ label: "Net revenue", data: (d.topCategories || []).map(function (c) { return c.revenue; }), backgroundColor: "#2b6cb0" }] },
                options: { maintainAspectRatio: false, indexAxis: "y", plugins: { legend: { display: false } } } });
            chart("chRoyalty", { type: "bar", data: { labels: ["Paid (range)", "Outstanding"], datasets: [{ data: [r.paid, r.outstanding], backgroundColor: ["#38a169", "#dd6b20"] }] },
                options: { maintainAspectRatio: false, plugins: { legend: { display: false } } } });

            document.getElementById("trendBook").onchange = async function () {
                const id = this.value;
                if (!id) return;
                try {
                    const t = (await R().api("/api/analytics/books/" + id + "/trend" + q)).data;
                    const existing = window.Chart && window.Chart.getChart ? window.Chart.getChart("chTrend") : null;
                    if (existing) existing.destroy();
                    chart("chTrend", { type: "bar", data: { labels: t.byMonth.map(function (m) { return m.month; }), datasets: [
                        { label: "Units", data: t.byMonth.map(function (m) { return m.booksSold; }), backgroundColor: "#dd6b20", yAxisID: "y" },
                        { label: "Net revenue", type: "line", data: t.byMonth.map(function (m) { return m.revenue; }), borderColor: "#2b6cb0", yAxisID: "y1" }] },
                        options: { maintainAspectRatio: false, scales: { y: { position: "left" }, y1: { position: "right", grid: { drawOnChartArea: false } } } } });
                } catch (e) { window.alert(e.message); }
            };
            bindDateRange("rangeForm", function (f, t) { V["analytics"]({ from: f, to: t }); });
        } catch (err) { fail(err); }
    };

    // ===================== SETTINGS (US33) =====================

    const KNOWN_SETTINGS = [
        { key: "currency", label: "Currency", type: "text", hint: "3-letter code, e.g. LKR" },
        { key: "taxRatePercent", label: "Tax rate (%)", type: "number", hint: "0 to 100, applied to new invoices" },
        { key: "invoicePrefix", label: "Invoice prefix", type: "text", hint: "e.g. INV- gives INV-2026-00001" },
        { key: "royaltyPaymentThreshold", label: "Royalty payment threshold", type: "number", hint: "Smaller payable amounts are carried forward" },
        { key: "companyName", label: "Company name", type: "text" },
        { key: "companyAddress", label: "Company address", type: "text" }
    ];

    V["settings"] = async function () {
        loading("System Settings");
        try {
            const [setRes, histRes] = await Promise.all([R().api("/api/settings"), R().api("/api/settings/history")]);
            const items = setRes.data || [], byKey = {};
            items.forEach(function (s) { byKey[s.settingKey] = s; });
            let inputs = "";
            KNOWN_SETTINGS.forEach(function (k) {
                const v = byKey[k.key] ? byKey[k.key].settingValue || "" : "";
                inputs += field(k.label, '<input type="' + k.type + '" id="set_' + k.key + '" value="' + h(v) + '"' +
                    (k.type === "number" ? ' step="0.01" min="0"' + (k.key === "taxRatePercent" ? ' max="100"' : "") : "") + ">", { hint: k.hint, full: k.key === "companyAddress" });
            });
            let other = "";
            items.filter(function (s) { return !KNOWN_SETTINGS.some(function (k) { return k.key === s.settingKey; }); }).forEach(function (s) {
                other += "<tr><td>" + h(s.settingKey) + "</td><td>" + h(s.settingValue || "-") + "</td><td>" + h(s.description || "-") + "</td></tr>";
            });
            let hist = "";
            (histRes.data || []).slice(0, 50).forEach(function (a) {
                hist += "<tr><td>" + h(String(a.createdAt).replace("T", " ").substring(0, 16)) + "</td><td>" + (a.userId != null ? "User #" + a.userId : "-") + "</td><td>" + h(a.details) + "</td></tr>";
            });
            R().content.innerHTML = '<div id="formMessage"></div>' +
                card("Finance Settings", '<form id="settingsForm" novalidate><div class="form-grid">' + inputs +
                    '</div><div class="form-actions"><button type="submit" class="btn btn-primary"><i class="fas fa-save"></i> Save Changes</button></div></form>',
                    "Changes apply to future invoices and calculations only. Existing records keep the values they were created with. Every change is logged below.") +
                card("Other Settings", '<form id="otherForm" novalidate><div class="form-grid">' +
                    field("Key", '<input type="text" id="settingKey" maxlength="100">', { required: true }) +
                    field("Value", '<input type="text" id="settingValue" maxlength="500">') +
                    field("Description", '<input type="text" id="settingDescription" maxlength="255">', { full: true }) +
                    '</div><div class="form-actions"><button type="submit" class="btn btn-secondary"><i class="fas fa-save"></i> Save</button></div></form>' +
                    table(["Key", "Value", "Description"], other, "No other settings.")) +
                card("Change History", table(["When", "By", "Change"], hist, "No changes recorded yet."));

            document.getElementById("settingsForm").onsubmit = function (e) {
                e.preventDefault();
                const changed = KNOWN_SETTINGS.filter(function (k) {
                    return val("set_" + k.key) !== (byKey[k.key] ? byKey[k.key].settingValue || "" : "");
                });
                if (!changed.length) { showMsg("formMessage", "success", "Nothing changed."); return; }
                const tax = val("set_taxRatePercent");
                if (tax !== "" && !(Number(tax) >= 0 && Number(tax) <= 100)) { R().setFieldError("set_taxRatePercent", "Tax rate must be between 0 and 100."); return; }
                act("formMessage", async function () {
                    for (const k of changed) {
                        await R().api("/api/settings", { method: "PUT", body: JSON.stringify({ settingKey: k.key, settingValue: val("set_" + k.key) }) });
                    }
                }, function () { V["settings"](); });
            };
            document.getElementById("otherForm").onsubmit = function (e) {
                e.preventDefault();
                if (!val("settingKey")) { R().setFieldError("settingKey", "Key is required."); return; }
                act("formMessage", function () {
                    return R().api("/api/settings", { method: "PUT", body: JSON.stringify({ settingKey: val("settingKey"), settingValue: val("settingValue") || null, description: val("settingDescription") || null }) });
                }, function () { V["settings"](); });
            };
        } catch (err) { fail(err); }
    };

    // ===================== ANNOUNCEMENTS (US34) =====================

    const AUDIENCES = ["ALL", "AUTHOR", "ADMIN", "FINANCE_STAFF", "EXECUTIVE", "EDITOR", "PROOFREADER", "DESIGNER",
        "PRODUCTION_MANAGER", "INVENTORY_STAFF", "SALES_STAFF", "CUSTOMER"];

    V["announcements"] = async function (editId) {
        loading("Announcements");
        try {
            const items = ((await R().api("/api/announcements")) || {}).data || [];
            const editing = editId ? items.find(function (a) { return a.announcementId === editId; }) : null;
            let rows = "";
            items.forEach(function (a) {
                rows += "<tr><td><strong>" + h(a.title) + "</strong><div class=\"muted\">" + h(String(a.content).substring(0, 120)) + "</div></td><td>" +
                    h(a.audience.replace(/_/g, " ")) + "</td><td>" + h(a.publishedAt ? a.publishedAt.substring(0, 10) : "-") + "</td><td>" +
                    h(a.expiresAt ? a.expiresAt.substring(0, 10) : "no end") + "</td><td>" + badge(a.status) + '</td><td class="actions-cell">' +
                    iconBtn("btn-edit", "Edit", "fa-edit", 'data-id="' + a.announcementId + '"') +
                    iconBtn("btn-delete", "Delete", "fa-trash", 'data-id="' + a.announcementId + '"') + "</td></tr>";
            });
            const isDraft = editing && editing.status === "DRAFT";
            R().content.innerHTML = '<div id="formMessage"></div>' +
                card(editing ? "Edit Announcement" : "New Announcement", '<form id="annForm" novalidate><div class="form-grid">' +
                    field("Title", '<input type="text" id="title" maxlength="200" lang="si" value="' + h(editing ? editing.title : "") + '">', { required: true, full: true }) +
                    field("Message", '<textarea id="annContent" rows="4" lang="si">' + h(editing ? editing.content : "") + "</textarea>", { required: true, full: true }) +
                    field("Audience", '<select id="audience">' + options(AUDIENCES, editing ? editing.audience : "ALL") + "</select>") +
                    field("Show from", '<input type="date" id="publishFrom" value="' + (editing && editing.publishedAt ? editing.publishedAt.substring(0, 10) : "") + '">', { hint: "Empty = now" }) +
                    field("Show until", '<input type="date" id="publishTo" value="' + (editing && editing.expiresAt ? editing.expiresAt.substring(0, 10) : "") + '">', { hint: "Empty = no end" }) +
                    '<div class="form-group"><label class="checkbox"><input type="checkbox" id="draft"' + (isDraft ? " checked" : "") + "> Save as draft (not shown)</label></div>" +
                    '</div><div class="form-actions"><button type="submit" class="btn btn-primary"><i class="fas fa-save"></i> ' + (editing ? "Save Changes" : "Publish") + "</button>" +
                    (editing ? '<button type="button" class="btn btn-secondary" id="cancelEdit">Cancel</button>' : "") + "</div></form>") +
                card("All Announcements (" + items.length + ")", '<div id="listMessage"></div>' + table(["Announcement", "Audience", "From", "Until", "Status", "Actions"], rows, "No announcements yet."));

            R().linkDateRange("publishFrom", "publishTo", {});
            const reload = function () { V["announcements"](); };
            const cancel = document.getElementById("cancelEdit");
            if (cancel) cancel.onclick = reload;
            on(".btn-edit", function (id) { V["announcements"](Number(id)); window.scrollTo({ top: 0, behavior: "smooth" }); });
            on(".btn-delete", function (id) {
                if (window.confirm("Delete this announcement?")) act("listMessage", function () { return R().api("/api/announcements/" + id, { method: "DELETE" }); }, reload);
            });
            document.getElementById("annForm").onsubmit = function (e) {
                e.preventDefault();
                if (!val("title") || !val("annContent")) { showMsg("formMessage", "error", "Title and message are required."); return; }
                if (val("publishFrom") && val("publishTo") && val("publishTo") < val("publishFrom")) { showMsg("formMessage", "error", "Show until cannot be before show from."); return; }
                const body = { title: val("title"), content: val("annContent"), audience: val("audience"), publishFrom: val("publishFrom") || null,
                    publishTo: val("publishTo") || null, draft: document.getElementById("draft").checked };
                act("formMessage", function () {
                    return editing ? R().api("/api/announcements/" + editing.announcementId, { method: "PUT", body: JSON.stringify(body) }) : post("/api/announcements", body);
                }, reload);
            };
        } catch (err) { fail(err); }
    };
})();

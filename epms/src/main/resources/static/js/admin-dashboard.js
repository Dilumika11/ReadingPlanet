(function () {
    "use strict";

    const API = {
        production: "/api/production",
        warehouse: "/api/warehouse"
    };

    function getToken() {
        return localStorage.getItem("rp_token") || sessionStorage.getItem("rp_token");
    }

    function getUser() {
        const raw = localStorage.getItem("rp_user") || sessionStorage.getItem("rp_user");
        if (!raw) return null;
        try { return JSON.parse(raw); } catch (e) { return null; }
    }

    function clearSession() {
        localStorage.removeItem("rp_token");
        localStorage.removeItem("rp_user");
        sessionStorage.removeItem("rp_token");
        sessionStorage.removeItem("rp_user");
    }

    function hasRole(user, roleName) {
        return user && user.roles && user.roles.indexOf(roleName) !== -1;
    }

    function isProductionStaff(user) {
        return hasRole(user, "PRODUCTION_MANAGER") || hasRole(user, "ADMIN");
    }

    function isWarehouseStaff(user) {
        return hasRole(user, "INVENTORY_STAFF") || hasRole(user, "ADMIN");
    }

    function isAdminRole(user) {
        return hasRole(user, "ADMIN");
    }

    // Finance and royalty screens: finance staff work in them; admin and
    // executives can open them read-only (the API enforces the same rule).
    function isFinanceRole(user) {
        return hasRole(user, "FINANCE_STAFF") || hasRole(user, "ADMIN") || hasRole(user, "EXECUTIVE");
    }

    function isFinanceStaff(user) {
        return hasRole(user, "FINANCE_STAFF");
    }

    function isExecutive(user) {
        return hasRole(user, "EXECUTIVE");
    }

    function isStaff(user) {
        return isProductionStaff(user) || isWarehouseStaff(user) || isFinanceRole(user) || isAdminRole(user);
    }

    function authHeaders() {
        return {
            "Content-Type": "application/json",
            "Authorization": "Bearer " + getToken()
        };
    }

    async function api(url, options) {
        options = options || {};
        options.headers = Object.assign({}, authHeaders(), options.headers || {});
        const res = await fetch(url, options);
        if (res.status === 401) {
            clearSession();
            window.location.replace("/admin-login.html");
            return null;
        }
        const data = await res.json().catch(function () { return {}; });
        if (res.status === 403) {
            throw new Error(data.message || "Access denied (403)");
        }
        if (!res.ok) {
            throw new Error(data.message || ("Request failed (" + res.status + ")"));
        }
        return data;
    }

    async function apiUpload(url, formData) {
        const res = await fetch(url, { method: "POST", headers: authHeaders(), body: formData });
        if (res.status === 401) {
            clearSession();
            window.location.replace("/admin-login.html");
            return null;
        }
        const data = await res.json().catch(function () { return {}; });
        if (!res.ok) throw new Error(data.message || ("Upload failed (" + res.status + ")"));
        return data;
    }

    function isEditableStatus(status) {
        const s = (status || "").toUpperCase();
        return s === "PENDING" || s === "SCHEDULED";
    }

    function todayISO() {
        const t = new Date();
        return t.getFullYear() + "-" +
            String(t.getMonth() + 1).padStart(2, "0") + "-" +
            String(t.getDate()).padStart(2, "0");
    }

    function money(v) {
        const n = Number(v);
        if (isNaN(n)) return "—";
        return "Rs " + n.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    function monthStartISO(monthsAgo) {
        const t = new Date();
        t.setDate(1);
        t.setMonth(t.getMonth() - monthsAgo);
        return t.getFullYear() + "-" + String(t.getMonth() + 1).padStart(2, "0") + "-01";
    }

    function lastDayOfPrevMonthISO() {
        const t = new Date();
        t.setDate(0); // day 0 of this month = last day of previous month
        return t.getFullYear() + "-" + String(t.getMonth() + 1).padStart(2, "0") + "-" +
            String(t.getDate()).padStart(2, "0");
    }

    // ---- Date / filter validation (mirrors com.epms.validation.DateRanges) ----
    const MAX_FILTER_YEARS = 5;
    const MAX_ROYALTY_PERIOD_DAYS = 366;

    function parseISO(s) {
        if (!s || !/^\d{4}-\d{2}-\d{2}$/.test(s)) return null;
        const d = new Date(s + "T00:00:00");
        return isNaN(d.getTime()) ? null : d;
    }

    function daysBetween(a, b) {
        return Math.round((b - a) / 86400000);
    }

    /** Returns an error string or null. Rules: ordered, start not in future, ≤ 5 years. */
    function validateFilterRange(from, to) {
        const f = parseISO(from), t = parseISO(to);
        if (!f || !t) return "Enter both dates as YYYY-MM-DD.";
        if (t < f) return "The end date cannot be before the start date.";
        if (f > parseISO(todayISO())) return "The start date is in the future — there are no sales to report yet.";
        if (daysBetween(f, t) > MAX_FILTER_YEARS * 366) return "Choose a range of " + MAX_FILTER_YEARS + " years or less.";
        return null;
    }

    /** Royalty sales period: ordered, entirely in the past, ≤ 1 year, inside the agreement's dates. */
    function validateRoyaltyPeriod(start, end, agreement) {
        const s = parseISO(start), e = parseISO(end);
        if (!s || !e) return "Enter both period dates as YYYY-MM-DD.";
        if (e < s) return "Period end cannot be before period start.";
        if (e > parseISO(todayISO())) return "Period end is in the future — royalties are calculated on completed sales only.";
        if (daysBetween(s, e) + 1 > MAX_ROYALTY_PERIOD_DAYS) return "A sales period cannot exceed one year — split it into shorter periods.";
        if (agreement) {
            if (s < parseISO(agreement.effectiveDate)) return "Period starts before the agreement's effective date (" + agreement.effectiveDate + ").";
            if (agreement.expiryDate && e > parseISO(agreement.expiryDate)) return "Period ends after the agreement expired (" + agreement.expiryDate + ").";
        }
        return null;
    }

    function validateAgreementDates(effective, expiry) {
        const ef = parseISO(effective);
        if (!ef) return "Enter the effective date as YYYY-MM-DD.";
        if (expiry) {
            const ex = parseISO(expiry);
            if (!ex) return "Enter the expiry date as YYYY-MM-DD.";
            if (ex <= ef) return "Expiry must be after the effective date.";
        }
        return null;
    }

    /** Show/clear an inline error under a field (and mark the input). */
    function setFieldError(inputId, message) {
        const input = document.getElementById(inputId);
        if (!input) return;
        let el = input.parentElement.querySelector(".field-error");
        if (message) {
            if (!el) { el = document.createElement("div"); el.className = "field-error"; input.parentElement.appendChild(el); }
            el.textContent = message;
            input.classList.add("invalid");
        } else {
            if (el) el.remove();
            input.classList.remove("invalid");
        }
    }

    /** Keep a start/end pair of calendars consistent: end.min = start, start.max = end (capped at today). */
    function linkDateRange(startId, endId, opts) {
        opts = opts || {};
        const start = document.getElementById(startId), end = document.getElementById(endId);
        if (!start || !end) return;
        const cap = opts.maxToday ? todayISO() : "";
        function sync() {
            if (cap) { start.max = end.value && end.value < cap ? end.value : cap; end.max = cap; }
            else if (end.value) start.max = end.value;
            if (start.value) end.min = start.value;
            if (opts.onChange) opts.onChange();
        }
        start.addEventListener("input", sync);
        end.addEventListener("input", sync);
        sync();
    }

    function escapeHtml(str) {
        if (str == null) return "";
        return String(str)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }

    const user = getUser();
    const token = getToken();

    if (!token || !user || !user.roles || !user.roles.length) {
        window.location.replace("/admin-login.html");
        return;
    }

    if (hasRole(user, "AUTHOR") && !isStaff(user)) {
        window.location.replace("/author/royalties.html");
        return;
    }

    if (!isStaff(user)) {
        document.body.innerHTML =
            '<div style="padding:3rem;text-align:center;font-family:sans-serif;">' +
            '<h2>Access Denied</h2>' +
            '<p>Your role does not have access to the staff dashboard.</p>' +
            '<p><a href="/admin-login.html">Back to login</a></p></div>';
        return;
    }

    document.getElementById("userName").textContent = user.fullName || user.email;
    document.getElementById("userRole").textContent =
        (user.roles || []).join(", ").replace(/_/g, " ");

    // Brand chrome: header "Account" pill shows who is signed in, and the
    // breadcrumb in the title band follows whatever screen sets #pageTitle.
    const headerUserName = document.getElementById("headerUserName");
    if (headerUserName) headerUserName.textContent = user.fullName || user.email;
    const crumbTitle = document.getElementById("crumbTitle");
    const pageTitleEl = document.getElementById("pageTitle");
    if (crumbTitle && pageTitleEl && window.MutationObserver) {
        new MutationObserver(function () { crumbTitle.textContent = pageTitleEl.textContent; })
            .observe(pageTitleEl, { childList: true, characterData: true, subtree: true });
    }

    const navItems = [];
    if (isProductionStaff(user)) {
        navItems.push(
            { id: "dashboard", label: "Dashboard", icon: "fa-tachometer-alt" },
            { id: "print-jobs", label: "Print Jobs", icon: "fa-print" },
            { id: "create-print-job", label: "Create Print Job", icon: "fa-plus-circle" }
        );
    }
    if (isWarehouseStaff(user)) {
        if (!isProductionStaff(user)) {
            navItems.push({ id: "wh-dashboard", label: "Dashboard", icon: "fa-tachometer-alt" });
        }
        navItems.push(
            { id: "inventory", label: "Inventory", icon: "fa-boxes" },
            { id: "receive-stock", label: "Receive Stock", icon: "fa-truck-loading" },
            { id: "adjust-stock", label: "Adjust Stock", icon: "fa-sliders-h" },
            { id: "transactions", label: "Transactions", icon: "fa-exchange-alt" }
        );
    }
    if (isAdminRole(user)) {
        navItems.push(
            { id: "epic4-dashboard", label: "Admin Dashboard", icon: "fa-chart-pie" },
            { id: "books", label: "Books", icon: "fa-book" },
            { id: "categories", label: "Categories", icon: "fa-tags" },
            { id: "genres", label: "Genres", icon: "fa-bookmark" },
            { id: "settings", label: "Settings", icon: "fa-cog" },
            { id: "announcements", label: "Announcements", icon: "fa-bullhorn" }
        );
    }
    if (isFinanceRole(user)) {
        if (!isAdminRole(user)) {
            navItems.push({ id: "epic4-dashboard", label: isExecutive(user) ? "Overview" : "Finance Dashboard", icon: "fa-chart-pie" });
        }
        navItems.push(
            { id: "analytics", label: "Executive Analytics", icon: "fa-chart-bar" },
            { id: "revenue", label: "Revenue", icon: "fa-chart-line" },
            { id: "expenses", label: "Expenses", icon: "fa-receipt" },
            { id: "invoices", label: "Invoices", icon: "fa-file-invoice" },
            { id: "payments", label: "Payments", icon: "fa-money-check-alt" },
            { id: "reports", label: "Financial Reports", icon: "fa-file-alt" },
            { id: "royalty-agreements", label: "Royalty Agreements", icon: "fa-file-contract" },
            { id: "royalty-calculations", label: "Royalty Calculations", icon: "fa-calculator" },
            { id: "royalty-approvals", label: "Statement Approvals", icon: "fa-check-double" },
            { id: "royalty-payments", label: "Royalty Payments", icon: "fa-hand-holding-usd" }
        );
    }

    const navEl = document.getElementById("sidebarNav");
    navItems.forEach(function (item) {
        const a = document.createElement("a");
        a.href = "#";
        a.dataset.view = item.id;
        a.innerHTML = '<i class="fas ' + item.icon + '"></i> ' + item.label;
        a.addEventListener("click", function (e) {
            e.preventDefault();
            navigate(item.id);
        });
        navEl.appendChild(a);
    });

    document.getElementById("logoutBtn").addEventListener("click", function () {
        clearSession();
        window.location.replace("/admin-login.html");
    });

    document.getElementById("sidebarToggle").addEventListener("click", function () {
        document.getElementById("sidebar").classList.toggle("open");
    });

    const content = document.getElementById("adminContent");
    const pageTitle = document.getElementById("pageTitle");

    function setActiveNav(viewId) {
        const map = { "edit-print-job": "print-jobs" };
        const active = map[viewId] || viewId;
        navEl.querySelectorAll("a").forEach(function (a) {
            a.classList.toggle("active", a.dataset.view === active);
        });
    }

    function navigate(viewId, param) {
        setActiveNav(viewId);
        if (viewId === "dashboard") renderDashboard();
        else if (viewId === "print-jobs") renderPrintJobs();
        else if (viewId === "create-print-job") renderCreatePrintJob();
        else if (viewId === "edit-print-job") renderEditPrintJob(param);
        else if (viewId === "wh-dashboard") renderWarehouseDashboard();
        else if (viewId === "inventory") renderInventory();
        else if (viewId === "receive-stock") renderReceiveStock();
        else if (viewId === "adjust-stock") renderAdjustStock();
        else if (viewId === "transactions") renderTransactions();
        else if (viewId === "books") renderBooks();
        else if (viewId === "categories") renderCategories();
        else if (viewId === "genres") renderGenres();
        // Epic 4 administration, finance, royalty and analytics screens live in js/epic4.js
        else if (window.RPViews && window.RPViews[viewId]) window.RPViews[viewId](param);
    }

    // ===================== PRODUCTION =====================

    async function renderDashboard() {
        pageTitle.textContent = "Production Dashboard";
        content.innerHTML = '<div class="empty-state"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';
        try {
            const ordersRes = await api(API.production + "/print-orders");
            const booksRes = await api(API.production + "/books/approved");
            const orders = (ordersRes && ordersRes.data) || [];
            const books = (booksRes && booksRes.data) || [];
            const pending = orders.filter(function (o) { return o.status === "PENDING"; }).length;
            const inProgress = orders.filter(function (o) {
                return o.status === "IN_PROGRESS" || o.status === "SCHEDULED";
            }).length;
            const completed = orders.filter(function (o) { return o.status === "COMPLETED"; }).length;

            content.innerHTML =
                '<div class="stats-grid">' +
                '  <div class="stat-card"><div class="stat-label">Approved Books</div><div class="stat-value">' + books.length + '</div></div>' +
                '  <div class="stat-card"><div class="stat-label">Pending Jobs</div><div class="stat-value">' + pending + '</div></div>' +
                '  <div class="stat-card"><div class="stat-label">In Progress</div><div class="stat-value">' + inProgress + '</div></div>' +
                '  <div class="stat-card"><div class="stat-label">Completed</div><div class="stat-value">' + completed + '</div></div>' +
                '</div>' +
                '<div class="content-card"><h2>Recent Print Jobs</h2><p class="card-desc">Latest print orders.</p>' +
                renderOrdersTable(orders.slice(0, 8), false) + '</div>';
        } catch (err) {
            content.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
        }
    }

    async function renderPrintJobs() {
        pageTitle.textContent = "Print Jobs";
        content.innerHTML = '<div class="empty-state"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';
        try {
            const res = await api(API.production + "/print-orders");
            const orders = (res && res.data) || [];
            content.innerHTML =
                '<div id="listMessage"></div>' +
                '<div class="content-card">' +
                '  <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:.75rem;margin-bottom:1rem;">' +
                '    <div><h2>All Print Jobs</h2><p class="card-desc" style="margin:0;">Edit/Delete only for PENDING or SCHEDULED.</p></div>' +
                '    <button type="button" class="btn btn-primary" id="btnNewJob"><i class="fas fa-plus"></i> Create Print Job</button>' +
                '  </div>' +
                renderOrdersTable(orders, true) +
                '</div>';
            document.getElementById("btnNewJob").addEventListener("click", function () {
                navigate("create-print-job");
            });
            bindRowActions();
        } catch (err) {
            content.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
        }
    }

    function renderOrdersTable(orders, withActions) {
        if (!orders.length) {
            return '<div class="empty-state">No print jobs yet.</div>';
        }
        let html = '<div class="table-wrap"><table class="data-table"><thead><tr>' +
            '<th>ID</th><th>Book</th><th>Qty</th><th>Printer</th><th>Order Date</th><th>Expected</th><th>Status</th><th>Requested By</th>';
        if (withActions) html += '<th>Actions</th>';
        html += '</tr></thead><tbody>';
        orders.forEach(function (o) {
            const statusClass = "badge-" + (o.status || "").toLowerCase().replace(/\s+/g, "_");
            html += "<tr><td>#" + o.printOrderId + "</td><td>" + escapeHtml(o.bookTitle || ("Book #" + o.bookId)) +
                "</td><td>" + o.quantity + "</td><td>" + escapeHtml(o.printingCompany || "—") +
                "</td><td>" + (o.orderDate || "—") + "</td><td>" + (o.expectedCompletionDate || "—") +
                '</td><td><span class="badge ' + statusClass + '">' + escapeHtml(o.status) + "</span></td><td>" +
                escapeHtml(o.requestedByName || "—") + "</td>";
            if (withActions) {
                html += '<td class="actions-cell">';
                if (isEditableStatus(o.status)) {
                    html += '<button type="button" class="btn-icon btn-edit" data-id="' + o.printOrderId +
                        '" title="Edit"><i class="fas fa-edit"></i></button> ' +
                        '<button type="button" class="btn-icon btn-delete" data-id="' + o.printOrderId +
                        '" title="Delete"><i class="fas fa-trash"></i></button>';
                } else {
                    html += '<span class="muted">—</span>';
                }
                html += "</td>";
            }
            html += "</tr>";
        });
        html += "</tbody></table></div>";
        return html;
    }

    function bindRowActions() {
        content.querySelectorAll(".btn-edit").forEach(function (btn) {
            btn.addEventListener("click", function () {
                navigate("edit-print-job", btn.getAttribute("data-id"));
            });
        });
        content.querySelectorAll(".btn-delete").forEach(function (btn) {
            btn.addEventListener("click", async function () {
                const id = btn.getAttribute("data-id");
                if (!confirm("Delete print job #" + id + "?")) return;
                const msgEl = document.getElementById("listMessage");
                try {
                    const res = await api(API.production + "/print-orders/" + id, { method: "DELETE" });
                    if (msgEl) {
                        msgEl.innerHTML = '<div class="alert alert-success">' +
                            escapeHtml((res && res.message) || "Deleted.") + "</div>";
                    }
                    setTimeout(function () { navigate("print-jobs"); }, 500);
                } catch (err) {
                    if (msgEl) msgEl.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + "</div>";
                    else alert(err.message);
                }
            });
        });
    }

    async function renderCreatePrintJob() {
        pageTitle.textContent = "Create Print Job";
        content.innerHTML = '<div class="empty-state"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';
        let books = [];
        try {
            const res = await api(API.production + "/books/approved");
            books = (res && res.data) || [];
        } catch (err) {
            content.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
            return;
        }
        if (!books.length) {
            content.innerHTML = '<div class="content-card"><h2>Create Print Job</h2>' +
                '<div class="alert alert-error">No approved books available.</div>' +
                '<button type="button" class="btn btn-secondary" id="btnBackJobs">Back</button></div>';
            document.getElementById("btnBackJobs").onclick = function () { navigate("print-jobs"); };
            return;
        }
        let options = '<option value="">— Select an approved book —</option>';
        books.forEach(function (b) {
            options += '<option value="' + b.bookId + '">' + escapeHtml(b.title) +
                " (ISBN: " + escapeHtml(b.isbn || "N/A") + ")</option>";
        });
        content.innerHTML =
            '<div class="content-card"><h2>Create Print Job</h2>' +
            '<p class="card-desc">Create a print order for an approved book.</p><div id="formMessage"></div>' +
            '<form id="createPrintJobForm" novalidate><div class="form-grid">' +
            '<div class="form-group full"><label>Approved Book <span class="req">*</span></label>' +
            '<select id="bookId" required>' + options + '</select></div>' +
            '<div class="form-group"><label>Number of Copies <span class="req">*</span></label>' +
            '<input type="number" id="quantity" min="1" max="100000" required></div>' +
            '<div class="form-group"><label>Expected Completion Date</label>' +
            '<input type="date" id="expectedCompletionDate">' +
            '<span class="hint">Must be today or a future date.</span></div>' +
            '<div class="form-group full"><label>Printing Company</label>' +
            '<input type="text" id="printingCompany" maxlength="150"></div>' +
            '</div><div class="form-actions">' +
            '<button type="submit" class="btn btn-primary" id="submitBtn"><i class="fas fa-check"></i> Create</button>' +
            '<button type="button" class="btn btn-secondary" id="cancelBtn">Cancel</button></div></form></div>';

        document.getElementById("expectedCompletionDate").min = todayISO();
        document.getElementById("cancelBtn").onclick = function () { navigate("print-jobs"); };
        document.getElementById("createPrintJobForm").onsubmit = async function (e) {
            e.preventDefault();
            const msgEl = document.getElementById("formMessage");
            msgEl.innerHTML = "";
            const bookId = document.getElementById("bookId").value;
            const quantity = parseInt(document.getElementById("quantity").value, 10);
            const expectedCompletionDate = document.getElementById("expectedCompletionDate").value || null;
            const printingCompany = document.getElementById("printingCompany").value.trim() || null;
            if (!bookId) { msgEl.innerHTML = '<div class="alert alert-error">Select a book.</div>'; return; }
            if (!quantity || quantity < 1) { msgEl.innerHTML = '<div class="alert alert-error">Quantity must be at least 1.</div>'; return; }
            if (expectedCompletionDate) {
                const selected = new Date(expectedCompletionDate + "T00:00:00");
                const start = new Date(); start.setHours(0,0,0,0);
                if (selected < start) {
                    msgEl.innerHTML = '<div class="alert alert-error">Date cannot be in the past.</div>';
                    return;
                }
            }
            const btn = document.getElementById("submitBtn");
            btn.disabled = true;
            try {
                const res = await api(API.production + "/print-orders", {
                    method: "POST",
                    body: JSON.stringify({ bookId: Number(bookId), quantity: quantity, printingCompany: printingCompany, expectedCompletionDate: expectedCompletionDate })
                });
                msgEl.innerHTML = '<div class="alert alert-success">' + escapeHtml(res.message || "Created.") + '</div>';
                setTimeout(function () { navigate("print-jobs"); }, 800);
            } catch (err) {
                msgEl.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
            } finally {
                btn.disabled = false;
            }
        };
    }

    async function renderEditPrintJob(id) {
        pageTitle.textContent = "Edit Print Job #" + id;
        content.innerHTML = '<div class="empty-state"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';
        let order;
        try {
            const res = await api(API.production + "/print-orders/" + id);
            order = res && res.data;
            if (!order) { content.innerHTML = '<div class="alert alert-error">Not found.</div>'; return; }
            if (!isEditableStatus(order.status)) {
                content.innerHTML = '<div class="content-card"><div class="alert alert-error">Only PENDING/SCHEDULED can be edited.</div>' +
                    '<button type="button" class="btn btn-secondary" id="btnBack">Back</button></div>';
                document.getElementById("btnBack").onclick = function () { navigate("print-jobs"); };
                return;
            }
        } catch (err) {
            content.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
            return;
        }
        const statusOptions = ["PENDING", "SCHEDULED", "IN_PROGRESS", "COMPLETED", "CANCELLED"]
            .map(function (s) {
                return '<option value="' + s + '"' + (order.status === s ? " selected" : "") + ">" + s + "</option>";
            }).join("");
        content.innerHTML =
            '<div class="content-card"><h2>Edit Print Job #' + order.printOrderId + '</h2>' +
            '<p class="card-desc">Book: <strong>' + escapeHtml(order.bookTitle || "") + '</strong></p>' +
            '<div id="formMessage"></div><form id="editForm"><div class="form-grid">' +
            '<div class="form-group"><label>Quantity <span class="req">*</span></label>' +
            '<input type="number" id="quantity" min="1" value="' + order.quantity + '" required></div>' +
            '<div class="form-group"><label>Expected Date</label>' +
            '<input type="date" id="expectedCompletionDate" value="' + (order.expectedCompletionDate || "") + '"></div>' +
            '<div class="form-group"><label>Status</label><select id="status">' + statusOptions + '</select></div>' +
            '<div class="form-group"><label>Printing Company</label>' +
            '<input type="text" id="printingCompany" value="' + escapeHtml(order.printingCompany || "") + '"></div>' +
            '</div><div class="form-actions">' +
            '<button type="submit" class="btn btn-primary" id="submitBtn"><i class="fas fa-save"></i> Save</button>' +
            '<button type="button" class="btn btn-secondary" id="cancelBtn">Cancel</button>' +
            '<button type="button" class="btn btn-danger" id="deleteBtn"><i class="fas fa-trash"></i> Delete</button>' +
            '</div></form></div>';
        document.getElementById("expectedCompletionDate").min = todayISO();
        document.getElementById("cancelBtn").onclick = function () { navigate("print-jobs"); };
        document.getElementById("deleteBtn").onclick = async function () {
            if (!confirm("Delete?")) return;
            try {
                await api(API.production + "/print-orders/" + id, { method: "DELETE" });
                navigate("print-jobs");
            } catch (err) {
                document.getElementById("formMessage").innerHTML =
                    '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
            }
        };
        document.getElementById("editForm").onsubmit = async function (e) {
            e.preventDefault();
            const msgEl = document.getElementById("formMessage");
            const quantity = parseInt(document.getElementById("quantity").value, 10);
            const expectedCompletionDate = document.getElementById("expectedCompletionDate").value || null;
            if (!quantity || quantity < 1) {
                msgEl.innerHTML = '<div class="alert alert-error">Quantity must be at least 1.</div>';
                return;
            }
            try {
                const res = await api(API.production + "/print-orders/" + id, {
                    method: "PUT",
                    body: JSON.stringify({
                        quantity: quantity,
                        printingCompany: document.getElementById("printingCompany").value.trim() || null,
                        expectedCompletionDate: expectedCompletionDate,
                        status: document.getElementById("status").value
                    })
                });
                msgEl.innerHTML = '<div class="alert alert-success">' + escapeHtml(res.message || "Updated.") + '</div>';
                setTimeout(function () { navigate("print-jobs"); }, 700);
            } catch (err) {
                msgEl.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
            }
        };
    }

    // ===================== WAREHOUSE =====================

    async function renderWarehouseDashboard() {
        pageTitle.textContent = "Warehouse Dashboard";
        content.innerHTML = '<div class="empty-state"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';
        try {
            const invRes = await api(API.warehouse + "/inventory");
            const txRes = await api(API.warehouse + "/transactions");
            const inv = (invRes && invRes.data) || [];
            const txs = (txRes && txRes.data) || [];
            const lowStock = inv.filter(function (i) { return i.lowStock; }).length;
            const totalQty = inv.reduce(function (s, i) { return s + (i.quantityInStock || 0); }, 0);

            content.innerHTML =
                '<div class="stats-grid">' +
                '  <div class="stat-card"><div class="stat-label">Books in Inventory</div><div class="stat-value">' + inv.length + '</div></div>' +
                '  <div class="stat-card"><div class="stat-label">Total Units</div><div class="stat-value">' + totalQty + '</div></div>' +
                '  <div class="stat-card"><div class="stat-label">Low Stock Items</div><div class="stat-value">' + lowStock + '</div></div>' +
                '  <div class="stat-card"><div class="stat-label">Recent Receipts</div><div class="stat-value">' +
                txs.filter(function (t) { return t.transactionType === "RECEIPT"; }).length + '</div></div>' +
                '</div>' +
                '<div class="content-card"><h2>Current Inventory</h2>' + renderInventoryTable(inv.slice(0, 10)) + '</div>';
        } catch (err) {
            content.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
        }
    }

    async function renderInventory() {
        pageTitle.textContent = "Inventory";
        content.innerHTML = '<div class="empty-state"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';
        try {
            const res = await api(API.warehouse + "/inventory");
            const inv = (res && res.data) || [];
            content.innerHTML =
                '<div class="content-card">' +
                '  <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:.75rem;margin-bottom:1rem;">' +
                '    <div><h2>Stock Levels</h2><p class="card-desc" style="margin:0;">Current warehouse inventory by book.</p></div>' +
                '    <button type="button" class="btn btn-primary" id="btnReceive"><i class="fas fa-truck-loading"></i> Receive Stock</button>' +
                '  </div>' +
                renderInventoryTable(inv) +
                '</div>';
            document.getElementById("btnReceive").onclick = function () { navigate("receive-stock"); };
        } catch (err) {
            content.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
        }
    }

    function renderInventoryTable(items) {
        if (!items.length) {
            return '<div class="empty-state">No inventory records yet. Receive stock from completed print jobs.</div>';
        }
        let html = '<div class="table-wrap"><table class="data-table"><thead><tr>' +
            '<th>Book</th><th>ISBN</th><th>Qty in Stock</th><th>Reorder Level</th><th>Location</th><th>Last Update</th><th></th>' +
            '</tr></thead><tbody>';
        items.forEach(function (i) {
            html += "<tr><td>" + escapeHtml(i.bookTitle || ("Book #" + i.bookId)) +
                "</td><td>" + escapeHtml(i.isbn || "—") +
                "</td><td><strong>" + i.quantityInStock + "</strong></td><td>" + i.reorderLevel +
                "</td><td>" + escapeHtml(i.warehouseLocation || "—") +
                "</td><td>" + (i.lastStockUpdate ? String(i.lastStockUpdate).replace("T", " ").substring(0, 16) : "—") +
                "</td><td>" + (i.lowStock ? '<span class="badge badge-pending">LOW STOCK</span>' : "") +
                "</td></tr>";
        });
        html += "</tbody></table></div>";
        return html;
    }

    async function renderReceiveStock() {
        pageTitle.textContent = "Receive Stock";
        content.innerHTML = '<div class="empty-state"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';

        let completed = [];
        try {
            const res = await api(API.warehouse + "/print-orders/completed");
            completed = (res && res.data) || [];
        } catch (err) {
            content.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
            return;
        }

        // Only show orders that still have remaining quantity
        const available = completed.filter(function (o) { return o.remainingToReceive > 0; });

        let options = '<option value="">— Select a completed print order —</option>';
        available.forEach(function (o) {
            options += '<option value="' + o.printOrderId + '" data-remaining="' + o.remainingToReceive +
                '" data-book="' + escapeHtml(o.bookTitle || "") + '">' +
                "#" + o.printOrderId + " — " + escapeHtml(o.bookTitle || ("Book #" + o.bookId)) +
                " (ordered: " + o.quantity + ", remaining: " + o.remainingToReceive + ")" +
                "</option>";
        });

        content.innerHTML =
            '<div class="content-card">' +
            '  <h2>Receive Printed Books into Inventory</h2>' +
            '  <p class="card-desc">Record books received from a completed print job so stock levels stay accurate.</p>' +
            '  <div id="formMessage"></div>' +
            (available.length === 0
                ? '<div class="alert alert-error">No completed print orders with remaining quantity to receive. Mark a print job as COMPLETED in Production first.</div>' +
                  '<button type="button" class="btn btn-secondary" id="btnBackInv">Back to Inventory</button>'
                : '<form id="receiveForm" novalidate><div class="form-grid">' +
                  '<div class="form-group full"><label>Completed Print Order <span class="req">*</span></label>' +
                  '<select id="printOrderId" required>' + options + '</select>' +
                  '<span class="hint">Only COMPLETED print jobs with remaining quantity are listed.</span></div>' +
                  '<div class="form-group"><label>Quantity Received <span class="req">*</span></label>' +
                  '<input type="number" id="quantityReceived" min="1" required placeholder="e.g. 500">' +
                  '<span class="hint" id="remainingHint"></span></div>' +
                  '<div class="form-group"><label>Warehouse Location</label>' +
                  '<input type="text" id="warehouseLocation" maxlength="100" placeholder="e.g. Aisle 3 / Shelf B"></div>' +
                  '<div class="form-group full"><label>Remarks</label>' +
                  '<textarea id="remarks" rows="2" maxlength="1000" placeholder="Optional notes"></textarea></div>' +
                  '</div><div class="form-actions">' +
                  '<button type="submit" class="btn btn-primary" id="submitBtn"><i class="fas fa-check"></i> Record Receipt</button>' +
                  '<button type="button" class="btn btn-secondary" id="cancelBtn">Cancel</button>' +
                  '</div></form>') +
            '</div>';

        if (available.length === 0) {
            document.getElementById("btnBackInv").onclick = function () { navigate("inventory"); };
            return;
        }

        document.getElementById("printOrderId").addEventListener("change", function () {
            const opt = this.options[this.selectedIndex];
            const remaining = opt.getAttribute("data-remaining");
            const hint = document.getElementById("remainingHint");
            const qty = document.getElementById("quantityReceived");
            if (remaining) {
                hint.textContent = "Maximum remaining for this order: " + remaining;
                qty.max = remaining;
            } else {
                hint.textContent = "";
                qty.removeAttribute("max");
            }
        });

        document.getElementById("cancelBtn").onclick = function () { navigate("inventory"); };

        document.getElementById("receiveForm").onsubmit = async function (e) {
            e.preventDefault();
            const msgEl = document.getElementById("formMessage");
            msgEl.innerHTML = "";

            const printOrderId = document.getElementById("printOrderId").value;
            const quantityReceived = parseInt(document.getElementById("quantityReceived").value, 10);
            const warehouseLocation = document.getElementById("warehouseLocation").value.trim() || null;
            const remarks = document.getElementById("remarks").value.trim() || null;

            if (!printOrderId) {
                msgEl.innerHTML = '<div class="alert alert-error">Please select a print order.</div>';
                return;
            }
            if (!quantityReceived || quantityReceived < 1) {
                msgEl.innerHTML = '<div class="alert alert-error">Quantity received must be at least 1.</div>';
                return;
            }

            const opt = document.getElementById("printOrderId").options[
                document.getElementById("printOrderId").selectedIndex];
            const remaining = parseInt(opt.getAttribute("data-remaining"), 10);
            if (remaining && quantityReceived > remaining) {
                msgEl.innerHTML = '<div class="alert alert-error">Cannot receive more than remaining (' + remaining + ').</div>';
                return;
            }

            const btn = document.getElementById("submitBtn");
            btn.disabled = true;
            btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving...';

            try {
                const res = await api(API.warehouse + "/inventory/receive", {
                    method: "POST",
                    body: JSON.stringify({
                        printOrderId: Number(printOrderId),
                        quantityReceived: quantityReceived,
                        warehouseLocation: warehouseLocation,
                        remarks: remarks
                    })
                });
                msgEl.innerHTML = '<div class="alert alert-success">' +
                    escapeHtml((res && res.message) || "Stock received.") + '</div>';
                setTimeout(function () { navigate("inventory"); }, 900);
            } catch (err) {
                msgEl.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
            } finally {
                btn.disabled = false;
                btn.innerHTML = '<i class="fas fa-check"></i> Record Receipt';
            }
        };
    }


    async function renderAdjustStock() {
        pageTitle.textContent = "Adjust Stock";
        content.innerHTML = '<div class="empty-state"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';

        let inv = [];
        try {
            const res = await api(API.warehouse + "/inventory");
            inv = (res && res.data) || [];
        } catch (err) {
            content.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
            return;
        }

        if (!inv.length) {
            content.innerHTML =
                '<div class="content-card">' +
                '  <h2>Adjust / Record Damaged or Returned Stock</h2>' +
                '  <div class="alert alert-error">No inventory records yet. Receive stock from completed print jobs first.</div>' +
                '  <button type="button" class="btn btn-secondary" id="btnBackInv">Back to Inventory</button>' +
                '</div>';
            document.getElementById("btnBackInv").onclick = function () { navigate("inventory"); };
            return;
        }

        let options = '<option value="">— Select a book in inventory —</option>';
        inv.forEach(function (i) {
            options += '<option value="' + i.bookId + '" data-qty="' + i.quantityInStock + '">' +
                escapeHtml(i.bookTitle || ("Book #" + i.bookId)) +
                " (in stock: " + i.quantityInStock + ")" +
                "</option>";
        });

        content.innerHTML =
            '<div class="content-card">' +
            '  <h2>Adjust / Record Damaged or Returned Stock</h2>' +
            '  <p class="card-desc">Update inventory when books are damaged, returned by customers, returned to the printer, or need a manual correction.</p>' +
            '  <div id="formMessage"></div>' +
            '  <form id="adjustForm" novalidate>' +
            '    <div class="form-grid">' +
            '      <div class="form-group full">' +
            '        <label for="bookId">Book <span class="req">*</span></label>' +
            '        <select id="bookId" required>' + options + '</select>' +
            '        <span class="hint" id="stockHint"></span>' +
            '      </div>' +
            '      <div class="form-group">' +
            '        <label for="adjustmentType">Adjustment Type <span class="req">*</span></label>' +
            '        <select id="adjustmentType" required>' +
            '          <option value="">— Select type —</option>' +
            '          <option value="DAMAGED">Damaged (remove from stock)</option>' +
            '          <option value="RETURN_IN">Customer / bookstore return (add to stock)</option>' +
            '          <option value="RETURN_OUT">Return to printer / write-off (remove from stock)</option>' +
            '          <option value="ADJUSTMENT">Manual adjustment</option>' +
            '        </select>' +
            '      </div>' +
            '      <div class="form-group" id="directionGroup" style="display:none;">' +
            '        <label for="direction">Direction <span class="req">*</span></label>' +
            '        <select id="direction">' +
            '          <option value="IN">Add to stock (IN)</option>' +
            '          <option value="OUT">Remove from stock (OUT)</option>' +
            '        </select>' +
            '      </div>' +
            '      <div class="form-group">' +
            '        <label for="quantity">Quantity <span class="req">*</span></label>' +
            '        <input type="number" id="quantity" min="1" required placeholder="e.g. 10">' +
            '      </div>' +
            '      <div class="form-group full">' +
            '        <label for="remarks">Remarks</label>' +
            '        <textarea id="remarks" rows="3" maxlength="1000" placeholder="Reason for adjustment (recommended for audit)"></textarea>' +
            '      </div>' +
            '    </div>' +
            '    <div class="form-actions">' +
            '      <button type="submit" class="btn btn-primary" id="submitBtn"><i class="fas fa-check"></i> Save Adjustment</button>' +
            '      <button type="button" class="btn btn-secondary" id="cancelBtn">Cancel</button>' +
            '    </div>' +
            '  </form>' +
            '</div>';

        document.getElementById("bookId").addEventListener("change", function () {
            const opt = this.options[this.selectedIndex];
            const qty = opt.getAttribute("data-qty");
            document.getElementById("stockHint").textContent =
                qty != null && this.value ? ("Current quantity in stock: " + qty) : "";
        });

        document.getElementById("adjustmentType").addEventListener("change", function () {
            document.getElementById("directionGroup").style.display =
                this.value === "ADJUSTMENT" ? "flex" : "none";
        });

        document.getElementById("cancelBtn").onclick = function () { navigate("inventory"); };

        document.getElementById("adjustForm").onsubmit = async function (e) {
            e.preventDefault();
            const msgEl = document.getElementById("formMessage");
            msgEl.innerHTML = "";

            const bookId = document.getElementById("bookId").value;
            const adjustmentType = document.getElementById("adjustmentType").value;
            const quantity = parseInt(document.getElementById("quantity").value, 10);
            const direction = document.getElementById("direction").value;
            const remarks = document.getElementById("remarks").value.trim() || null;

            if (!bookId) {
                msgEl.innerHTML = '<div class="alert alert-error">Please select a book.</div>';
                return;
            }
            if (!adjustmentType) {
                msgEl.innerHTML = '<div class="alert alert-error">Please select an adjustment type.</div>';
                return;
            }
            if (!quantity || quantity < 1) {
                msgEl.innerHTML = '<div class="alert alert-error">Quantity must be at least 1.</div>';
                return;
            }

            const btn = document.getElementById("submitBtn");
            btn.disabled = true;
            btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving...';

            try {
                const body = {
                    bookId: Number(bookId),
                    adjustmentType: adjustmentType,
                    quantity: quantity,
                    remarks: remarks
                };
                if (adjustmentType === "ADJUSTMENT") {
                    body.direction = direction;
                }

                const res = await api(API.warehouse + "/inventory/adjust", {
                    method: "POST",
                    body: JSON.stringify(body)
                });
                msgEl.innerHTML = '<div class="alert alert-success">' +
                    escapeHtml((res && res.message) || "Adjustment saved.") + '</div>';
                setTimeout(function () { navigate("inventory"); }, 900);
            } catch (err) {
                msgEl.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
            } finally {
                btn.disabled = false;
                btn.innerHTML = '<i class="fas fa-check"></i> Save Adjustment';
            }
        };
    }

    async function renderTransactions() {
        pageTitle.textContent = "Inventory Transactions";
        content.innerHTML = '<div class="empty-state"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';
        try {
            const res = await api(API.warehouse + "/transactions");
            const txs = (res && res.data) || [];
            if (!txs.length) {
                content.innerHTML = '<div class="content-card"><h2>Transactions</h2>' +
                    '<div class="empty-state">No transactions yet.</div></div>';
                return;
            }
            let html = '<div class="content-card"><h2>Recent Transactions</h2>' +
                '<div class="table-wrap"><table class="data-table"><thead><tr>' +
                '<th>ID</th><th>Type</th><th>Book</th><th>Qty</th><th>Ref</th><th>By</th><th>Date</th><th>Remarks</th>' +
                '</tr></thead><tbody>';
            txs.forEach(function (t) {
                html += "<tr><td>#" + t.transactionId +
                    '</td><td><span class="badge badge-scheduled">' + escapeHtml(t.transactionType) + "</span></td><td>" +
                    escapeHtml(t.bookTitle || "—") + "</td><td>" + t.quantity +
                    "</td><td>" + (t.referenceType ? escapeHtml(t.referenceType) + " #" + t.referenceId : "—") +
                    "</td><td>" + escapeHtml(t.performedByName || "—") +
                    "</td><td>" + (t.transactionDate ? String(t.transactionDate).replace("T", " ").substring(0, 16) : "—") +
                    "</td><td>" + escapeHtml(t.remarks || "—") + "</td></tr>";
            });
            html += "</tbody></table></div></div>";
            content.innerHTML = html;
        } catch (err) {
            content.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
        }
    }

    // ===================== EPIC 4: ADMIN / FINANCE / ROYALTY =====================

    async function renderBooks(editId) {
        pageTitle.textContent = "Books";
        content.innerHTML = '<div class="empty-state"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';
        try {
            const [booksRes, catsRes, genresRes] = await Promise.all([
                api("/api/books"), api("/api/categories"), api("/api/genres")
            ]);
            const books = (booksRes && booksRes.data) || [];
            const cats = ((catsRes && catsRes.data) || []);
            const genres = ((genresRes && genresRes.data) || []);
            const catName = {}; cats.forEach(function (c) { catName[c.categoryId] = c.categoryName; });
            const genreName = {}; genres.forEach(function (g) { genreName[g.genreId] = g.genreName; });
            const editing = editId ? books.find(function (b) { return b.bookId === editId; }) : null;

            let catOptions = '<option value="">— Select a category —</option>';
            cats.forEach(function (c) {
                catOptions += '<option value="' + c.categoryId + '"' + (editing && editing.categoryId === c.categoryId ? " selected" : "") + '>' +
                    escapeHtml(c.categoryName) + "</option>";
            });
            let genreOptions = '<option value="">— None —</option>';
            genres.forEach(function (g) {
                    genreOptions += '<option value="' + g.genreId + '"' + (editing && editing.genreId === g.genreId ? " selected" : "") + '>' +
                        escapeHtml(g.genreName) + "</option>";
                });

            let rows = "";
            books.forEach(function (b) {
                const cover = b.coverImage
                    ? '<img class="book-thumb" src="/uploads/covers/' + escapeHtml(b.coverImage) + '" alt="">'
                    : '<div class="book-thumb book-thumb-empty" title="No cover yet"><i class="fas fa-image"></i></div>';
                rows += "<tr><td>" + cover + "</td>" +
                    "<td><strong>" + escapeHtml(b.title) + "</strong><div class=\"muted\">" + escapeHtml(b.authorName) + "</div></td>" +
                    "<td>" + escapeHtml(catName[b.categoryId] || "—") + (b.genreId ? '<div class="muted">' + escapeHtml(genreName[b.genreId] || "") + "</div>" : "") + "</td>" +
                    "<td>" + money(b.price) + "</td>" +
                    "<td>" + (b.newArrival ? '<span class="badge badge-calculated">New</span>' : "") + "</td>" +
                    '<td class="actions-cell">' +
                    '<label class="btn-icon btn-cover" title="Upload cover"><i class="fas fa-camera"></i>' +
                    '<input type="file" accept="image/jpeg,image/png,image/webp" data-id="' + b.bookId + '" hidden></label> ' +
                    '<button type="button" class="btn-icon btn-edit" data-id="' + b.bookId + '" title="Edit"><i class="fas fa-edit"></i></button> ' +
                    '<button type="button" class="btn-icon btn-delete" data-id="' + b.bookId + '" data-name="' + escapeHtml(b.title) + '" title="Delete"><i class="fas fa-trash"></i></button>' +
                    "</td></tr>";
            });

            content.innerHTML =
                '<div id="formMessage"></div>' +
                '<div class="content-card"><h2>' + (editing ? "Edit Book #" + editing.bookId : "Add a Book") + '</h2>' +
                '<p class="card-desc">Books appear in the Online Store under their category. Titles and author names can be in ' +
                'Sinhala (Unicode) — e.g. <span lang="si">සයිකෝ</span>. Upload the cover from the table after saving.</p>' +
                '<form id="createForm" novalidate><div class="form-grid">' +
                '<div class="form-group"><label>Title <span class="req">*</span></label>' +
                '<input type="text" id="title" lang="si" maxlength="255" required value="' + escapeHtml(editing ? editing.title : "") + '"></div>' +
                '<div class="form-group"><label>Author <span class="req">*</span></label>' +
                '<input type="text" id="authorName" lang="si" maxlength="150" required value="' + escapeHtml(editing ? editing.authorName : "") + '"></div>' +
                '<div class="form-group"><label>Category <span class="req">*</span></label>' +
                '<select id="categoryId" required>' + catOptions + '</select></div>' +
                '<div class="form-group"><label>Genre</label>' +
                '<select id="genreId">' + genreOptions + '</select></div>' +
                '<div class="form-group"><label>Price (Rs) <span class="req">*</span></label>' +
                '<input type="number" id="price" min="0" step="0.01" required value="' + (editing ? editing.price : "") + '"></div>' +
                '<div class="form-group"><label>ISBN</label>' +
                '<input type="text" id="isbn" maxlength="20" value="' + escapeHtml(editing ? (editing.isbn || "") : "") + '"></div>' +
                '<div class="form-group"><label>Author ID (Epic 1, optional)</label>' +
                '<input type="number" id="authorId" min="1" value="' + (editing && editing.authorId ? editing.authorId : "") + '">' +
                '<span class="hint">Links the book to the author\'s royalty agreements.</span></div>' +
                '<div class="form-group"><label>&nbsp;</label>' +
                '<label class="checkbox"><input type="checkbox" id="newArrival"' + (editing && editing.newArrival ? " checked" : "") + '> Show in New Arrivals</label></div>' +
                '<div class="form-group full"><label>Description</label>' +
                '<textarea id="description" lang="si" maxlength="2000" rows="3">' + escapeHtml(editing ? (editing.description || "") : "") + '</textarea></div>' +
                '</div><div class="form-actions">' +
                '<button type="submit" class="btn btn-primary" id="submitBtn"><i class="fas fa-save"></i> ' + (editing ? "Save Changes" : "Add Book") + '</button>' +
                (editing ? '<button type="button" class="btn btn-secondary" id="cancelEdit">Cancel</button>' : "") +
                '</div></form></div>' +
                '<div class="content-card"><h2>Catalogue (' + books.length + ')</h2>' +
                '<div class="table-wrap"><table class="data-table"><thead><tr>' +
                "<th>Cover</th><th>Title / Author</th><th>Category / Genre</th><th>Price</th><th></th><th>Actions</th>" +
                "</tr></thead><tbody>" +
                (rows || '<tr><td colspan="6" class="empty-state">No books yet — add the first one above.</td></tr>') +
                "</tbody></table></div></div>";

            const msgEl = document.getElementById("formMessage");

            document.getElementById("createForm").onsubmit = async function (e) {
                e.preventDefault();
                const body = {
                    title: document.getElementById("title").value.trim(),
                    authorName: document.getElementById("authorName").value.trim(),
                    categoryId: Number(document.getElementById("categoryId").value) || null,
                    genreId: Number(document.getElementById("genreId").value) || null,
                    price: document.getElementById("price").value,
                    isbn: document.getElementById("isbn").value.trim(),
                    authorId: Number(document.getElementById("authorId").value) || null,
                    newArrival: document.getElementById("newArrival").checked,
                    description: document.getElementById("description").value.trim()
                };
                if (!body.title || !body.authorName || !body.categoryId || body.price === "") {
                    msgEl.innerHTML = '<div class="alert alert-error">Title, author, category and price are required.</div>';
                    return;
                }
                try {
                    if (editing) {
                        await api("/api/books/" + editing.bookId, { method: "PUT", body: JSON.stringify(body) });
                    } else {
                        await api("/api/books", { method: "POST", body: JSON.stringify(body) });
                    }
                    renderBooks();
                } catch (err) {
                    msgEl.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
                }
            };

            const cancel = document.getElementById("cancelEdit");
            if (cancel) cancel.onclick = function () { renderBooks(); };

            content.querySelectorAll(".btn-edit").forEach(function (btn) {
                btn.addEventListener("click", function () {
                    renderBooks(Number(btn.getAttribute("data-id")));
                    window.scrollTo({ top: 0, behavior: "smooth" });
                });
            });
            content.querySelectorAll(".btn-delete").forEach(function (btn) {
                btn.addEventListener("click", async function () {
                    if (!window.confirm('Delete "' + btn.getAttribute("data-name") + '" from the catalogue? Its cover image will be removed too. This cannot be undone.')) return;
                    try {
                        await api("/api/books/" + btn.getAttribute("data-id"), { method: "DELETE" });
                        renderBooks();
                    } catch (err) {
                        msgEl.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
                    }
                });
            });
            content.querySelectorAll(".btn-cover input[type=file]").forEach(function (input) {
                input.addEventListener("change", async function () {
                    if (!input.files || !input.files[0]) return;
                    const fd = new FormData();
                    fd.append("file", input.files[0]);
                    try {
                        await apiUpload("/api/books/" + input.getAttribute("data-id") + "/cover", fd);
                        renderBooks();
                    } catch (err) {
                        msgEl.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
                    }
                });
            });
        } catch (err) {
            content.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
        }
    }

    // ---- Generic add / edit / delete screen for the simple admin lists ----
    // cfg: { title, singular, endpoint, idKey, nameKey, viewId, fields: [{ key, label, required, maxlength, full, lang }] }
    async function renderCrud(cfg, editId) {
        pageTitle.textContent = cfg.title;
        content.innerHTML = '<div class="empty-state"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';
        try {
            const res = await api(cfg.endpoint);
            const items = (res && res.data) || [];
            const editing = editId ? items.find(function (i) { return i[cfg.idKey] === editId; }) : null;

            let rows = "";
            items.forEach(function (i) {
                rows += "<tr><td>#" + i[cfg.idKey] + "</td>";
                cfg.fields.forEach(function (f) {
                    rows += "<td>" + (f.key === cfg.nameKey ? "<strong>" + escapeHtml(i[f.key]) + "</strong>"
                        : escapeHtml(i[f.key] || "—")) + "</td>";
                });
                rows += '<td class="actions-cell">' +
                    '<button type="button" class="btn-icon btn-edit" data-id="' + i[cfg.idKey] + '" title="Edit"><i class="fas fa-edit"></i></button> ' +
                    '<button type="button" class="btn-icon btn-delete" data-id="' + i[cfg.idKey] + '" data-name="' + escapeHtml(i[cfg.nameKey]) + '" title="Delete"><i class="fas fa-trash"></i></button>' +
                    "</td></tr>";
            });

            let inputs = "";
            cfg.fields.forEach(function (f) {
                inputs += '<div class="form-group' + (f.full ? " full" : "") + '"><label>' + escapeHtml(f.label) +
                    (f.required ? ' <span class="req">*</span>' : "") + "</label>" +
                    '<input type="text" id="f_' + f.key + '"' + (f.maxlength ? ' maxlength="' + f.maxlength + '"' : "") +
                    (f.lang ? ' lang="' + f.lang + '"' : "") + (f.required ? " required" : "") +
                    ' value="' + escapeHtml(editing ? (editing[f.key] || "") : "") + '"></div>';
            });

            content.innerHTML =
                '<div id="formMessage"></div>' +
                '<div class="content-card"><h2>' + (editing ? "Edit " + cfg.singular + " #" + editing[cfg.idKey] : "New " + cfg.singular) + '</h2>' +
                '<form id="createForm" novalidate><div class="form-grid">' + inputs + '</div>' +
                '<div class="form-actions"><button type="submit" class="btn btn-primary" id="submitBtn">' +
                '<i class="fas ' + (editing ? "fa-save" : "fa-plus") + '"></i> ' + (editing ? "Save Changes" : "Create") + "</button>" +
                (editing ? '<button type="button" class="btn btn-secondary" id="cancelEdit">Cancel</button>' : "") +
                "</div></form></div>" +
                '<div class="content-card"><h2>All ' + cfg.title + " (" + items.length + ')</h2><div id="listMessage"></div>' +
                '<div class="table-wrap"><table class="data-table"><thead><tr><th>ID</th>' +
                cfg.fields.map(function (f) { return "<th>" + escapeHtml(f.label) + "</th>"; }).join("") +
                "<th>Actions</th></tr></thead><tbody>" +
                (rows || '<tr><td colspan="' + (cfg.fields.length + 2) + '" class="empty-state">No ' + cfg.title.toLowerCase() + ' yet.</td></tr>') +
                "</tbody></table></div></div>";

            const msgEl = document.getElementById("formMessage");
            const listMsg = document.getElementById("listMessage");

            document.getElementById("createForm").onsubmit = async function (e) {
                e.preventDefault();
                const body = {};
                let missing = null;
                cfg.fields.forEach(function (f) {
                    const v = document.getElementById("f_" + f.key).value.trim();
                    body[f.key] = v || null;
                    if (f.required && !v && !missing) missing = f.label;
                });
                if (missing) { msgEl.innerHTML = '<div class="alert alert-error">' + escapeHtml(missing) + ' is required.</div>'; return; }
                try {
                    if (editing) {
                        await api(cfg.endpoint + "/" + editing[cfg.idKey], { method: "PUT", body: JSON.stringify(body) });
                    } else {
                        await api(cfg.endpoint, { method: "POST", body: JSON.stringify(body) });
                    }
                    renderCrud(cfg);
                } catch (err) {
                    msgEl.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
                }
            };

            const cancel = document.getElementById("cancelEdit");
            if (cancel) cancel.onclick = function () { renderCrud(cfg); };

            content.querySelectorAll(".btn-edit").forEach(function (btn) {
                btn.addEventListener("click", function () {
                    renderCrud(cfg, Number(btn.getAttribute("data-id")));
                    window.scrollTo({ top: 0, behavior: "smooth" });
                });
            });
            content.querySelectorAll(".btn-delete").forEach(function (btn) {
                btn.addEventListener("click", async function () {
                    if (!window.confirm('Delete ' + cfg.singular.toLowerCase() + ' "' + btn.getAttribute("data-name") + '"? This cannot be undone.')) return;
                    try {
                        await api(cfg.endpoint + "/" + btn.getAttribute("data-id"), { method: "DELETE" });
                        renderCrud(cfg);
                    } catch (err) {
                        listMsg.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
                    }
                });
            });
        } catch (err) {
            content.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
        }
    }

    const CRUD_CATEGORIES = {
        title: "Categories", singular: "Category", endpoint: "/api/categories", idKey: "categoryId", nameKey: "categoryName",
        fields: [
            { key: "categoryName", label: "Name", required: true, maxlength: 100, lang: "si" },
            { key: "description", label: "Description", maxlength: 255, full: true, lang: "si" }
        ]
    };
    const CRUD_GENRES = {
        title: "Genres", singular: "Genre", endpoint: "/api/genres", idKey: "genreId", nameKey: "genreName",
        fields: [
            { key: "genreName", label: "Name", required: true, maxlength: 100, lang: "si" },
            { key: "description", label: "Description", maxlength: 255, full: true, lang: "si" }
        ]
    };
    function renderCategories() { return renderCrud(CRUD_CATEGORIES); }
    function renderGenres() { return renderCrud(CRUD_GENRES); }

    // Shared helpers for the Epic 4 screens in js/epic4.js
    window.RP = {
        api: api, apiUpload: apiUpload, getToken: getToken, user: user,
        content: content, pageTitle: pageTitle, navigate: navigate,
        money: money, escapeHtml: escapeHtml, todayISO: todayISO, monthStartISO: monthStartISO,
        lastDayOfPrevMonthISO: lastDayOfPrevMonthISO, parseISO: parseISO,
        validateFilterRange: validateFilterRange, validateRoyaltyPeriod: validateRoyaltyPeriod,
        validateAgreementDates: validateAgreementDates, setFieldError: setFieldError, linkDateRange: linkDateRange,
        isAdmin: isAdminRole(user), isFinanceStaff: isFinanceStaff(user), isExecutive: isExecutive(user)
    };

    // Default landing page by role
    if (isExecutive(user) && !isAdminRole(user)) {
        navigate("analytics");
    } else if (isAdminRole(user) || isFinanceRole(user)) {
        navigate("epic4-dashboard");
    } else if (isProductionStaff(user) && !isWarehouseStaff(user)) {
        navigate("dashboard");
    } else if (isWarehouseStaff(user) && !isProductionStaff(user)) {
        navigate("wh-dashboard");
    } else {
        navigate("dashboard");
    }
})();

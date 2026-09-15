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

    function isFinanceRole(user) {
        return hasRole(user, "FINANCE_STAFF") || hasRole(user, "ADMIN");
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
            navItems.push({ id: "epic4-dashboard", label: "Finance Dashboard", icon: "fa-chart-pie" });
        }
        navItems.push(
            { id: "revenue", label: "Revenue", icon: "fa-chart-line" },
            { id: "royalty-agreements", label: "Royalty Agreements", icon: "fa-file-contract" },
            { id: "royalty-calculations", label: "Royalty Calculations", icon: "fa-calculator" }
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
        else if (viewId === "epic4-dashboard") renderEpic4Dashboard();
        else if (viewId === "revenue") renderRevenue();
        else if (viewId === "books") renderBooks();
        else if (viewId === "categories") renderCategories();
        else if (viewId === "genres") renderGenres();
        else if (viewId === "settings") renderSettings();
        else if (viewId === "announcements") renderAnnouncements();
        else if (viewId === "royalty-agreements") renderRoyaltyAgreements();
        else if (viewId === "royalty-calculations") renderRoyaltyCalculations();
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

    async function renderEpic4Dashboard() {
        pageTitle.textContent = isAdminRole(user) ? "Admin Dashboard" : "Finance Dashboard";
        content.innerHTML = '<div class="empty-state"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';
        try {
            const res = await api("/api/admin/dashboard");
            const stats = (res && res.data) || {};
            let rev = null;
            try {
                const revRes = await api("/api/finance/revenue");
                rev = (revRes && revRes.data) || null;
            } catch (ignored) { /* revenue is optional on the dashboard */ }

            let html =
                '<div class="stats-grid">' +
                '  <div class="stat-card"><div class="stat-label">Categories</div><div class="stat-value">' +
                (stats.totalCategories != null ? stats.totalCategories : "—") + '</div></div>' +
                '  <div class="stat-card"><div class="stat-label">Genres</div><div class="stat-value">' +
                (stats.totalGenres != null ? stats.totalGenres : "—") + '</div></div>' +
                '  <div class="stat-card"><div class="stat-label">Announcements</div><div class="stat-value">' +
                (stats.totalAnnouncements != null ? stats.totalAnnouncements : "—") + '</div></div>' +
                '  <div class="stat-card"><div class="stat-label">Active Royalty Agreements</div><div class="stat-value">' +
                (stats.activeRoyaltyAgreements != null ? stats.activeRoyaltyAgreements : "—") + '</div></div>' +
                '</div>';

            if (rev) {
                html +=
                    '<div class="stats-grid">' +
                    '  <div class="stat-card"><div class="stat-label">Revenue (last 12 months)</div><div class="stat-value">' +
                    money(rev.totalRevenue) + '</div></div>' +
                    '  <div class="stat-card"><div class="stat-label">Completed Sales</div><div class="stat-value">' +
                    rev.completedSales + '</div></div>' +
                    '  <div class="stat-card"><div class="stat-label">Books Sold</div><div class="stat-value">' +
                    rev.booksSold + '</div></div>' +
                    '  <div class="stat-card"><div class="stat-label">Royalties Calculated</div><div class="stat-value">' +
                    money(stats.totalRoyaltiesCalculated || 0) + '</div></div>' +
                    '</div>';
            }

            content.innerHTML = html;
        } catch (err) {
            content.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
        }
    }

    // ---- US38: Monitor revenue from completed sales ----
    async function renderRevenue(from, to) {
        pageTitle.textContent = "Revenue";
        from = from || monthStartISO(11);
        to = to || todayISO();
        content.innerHTML = '<div class="empty-state"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';
        try {
            const res = await api("/api/finance/revenue?from=" + from + "&to=" + to);
            const rev = (res && res.data) || {};
            const excluded = rev.excluded || {};
            const channels = rev.revenueByChannel || {};

            let monthRows = "";
            (rev.monthlyRevenue || []).forEach(function (m) {
                monthRows += "<tr><td>" + escapeHtml(m.month) + "</td><td>" + m.completedSales + "</td><td>" +
                    m.booksSold + "</td><td><strong>" + money(m.revenue) + "</strong></td></tr>";
            });

            let bookRows = "";
            (rev.topBooks || []).forEach(function (b) {
                bookRows += "<tr><td>#" + b.bookId + "</td><td>" + escapeHtml(b.bookTitle) + "</td><td>" +
                    b.booksSold + "</td><td><strong>" + money(b.revenue) + "</strong></td></tr>";
            });

            let channelRows = "";
            Object.keys(channels).forEach(function (k) {
                channelRows += "<tr><td>" + escapeHtml(k) + "</td><td><strong>" + money(channels[k]) + "</strong></td></tr>";
            });

            content.innerHTML =
                '<div class="content-card"><form id="filterForm" novalidate><div class="form-grid">' +
                '<div class="form-group"><label>From</label><input type="date" id="from" value="' + from + '"></div>' +
                '<div class="form-group"><label>To</label><input type="date" id="to" value="' + to + '"></div>' +
                '</div><div class="form-actions"><button type="submit" class="btn btn-primary">' +
                '<i class="fas fa-filter"></i> Apply</button></div></form></div>' +
                '<div class="stats-grid">' +
                '  <div class="stat-card"><div class="stat-label">Total Revenue</div><div class="stat-value">' + money(rev.totalRevenue) + '</div></div>' +
                '  <div class="stat-card"><div class="stat-label">Completed Sales</div><div class="stat-value">' + rev.completedSales + '</div></div>' +
                '  <div class="stat-card"><div class="stat-label">Books Sold</div><div class="stat-value">' + rev.booksSold + '</div></div>' +
                '  <div class="stat-card"><div class="stat-label">Average Sale</div><div class="stat-value">' + money(rev.averageSaleValue) + '</div></div>' +
                '</div>' +
                '<div class="content-card"><h2>Monthly Revenue</h2>' +
                '<div class="table-wrap"><table class="data-table"><thead><tr><th>Month</th><th>Completed Sales</th>' +
                "<th>Books Sold</th><th>Revenue</th></tr></thead><tbody>" +
                (monthRows || '<tr><td colspan="4" class="empty-state">No completed sales in this period.</td></tr>') +
                "</tbody></table></div></div>" +
                '<div class="content-card"><h2>Top Books</h2>' +
                '<div class="table-wrap"><table class="data-table"><thead><tr><th>Book</th><th>Title</th>' +
                "<th>Books Sold</th><th>Revenue</th></tr></thead><tbody>" +
                (bookRows || '<tr><td colspan="4" class="empty-state">No data.</td></tr>') +
                "</tbody></table></div></div>" +
                '<div class="content-card"><h2>Revenue by Channel</h2>' +
                '<div class="table-wrap"><table class="data-table"><thead><tr><th>Channel</th><th>Revenue</th></tr></thead><tbody>' +
                (channelRows || '<tr><td colspan="2" class="empty-state">No data.</td></tr>') +
                "</tbody></table></div></div>" +
                '<div class="content-card"><h2>Excluded from Revenue</h2>' +
                '<p class="card-desc">Received from Epic 3 but not counted — only completed sales contribute to revenue and royalties.</p>' +
                '<div class="table-wrap"><table class="data-table"><thead><tr><th>Status</th><th>Transactions</th><th>Amount</th></tr></thead><tbody>' +
                '<tr><td><span class="badge badge-cancelled">CANCELLED</span></td><td>' + (excluded.cancelledCount || 0) + "</td><td>" + money(excluded.cancelledAmount || 0) + "</td></tr>" +
                '<tr><td><span class="badge badge-returned">RETURNED</span></td><td>' + (excluded.returnedCount || 0) + "</td><td>" + money(excluded.returnedAmount || 0) + "</td></tr>" +
                "</tbody></table></div></div>";

            const filterMsg = document.createElement("div");
            document.getElementById("filterForm").prepend(filterMsg);
            linkDateRange("from", "to", { maxToday: true, onChange: function () {
                const err = validateFilterRange(document.getElementById("from").value, document.getElementById("to").value);
                setFieldError("to", err);
            } });
            document.getElementById("filterForm").onsubmit = function (e) {
                e.preventDefault();
                const f = document.getElementById("from").value, t = document.getElementById("to").value;
                const err = validateFilterRange(f, t);
                setFieldError("to", err);
                filterMsg.innerHTML = err ? '<div class="alert alert-error">' + escapeHtml(err) + '</div>' : "";
                if (err) return;
                renderRevenue(f, t);
            };
        } catch (err) {
            content.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
        }
    }

    // ---- Books: the online-store catalogue (title/author accept Sinhala Unicode) ----
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
    const CRUD_ANNOUNCEMENTS = {
        title: "Announcements", singular: "Announcement", endpoint: "/api/announcements", idKey: "announcementId", nameKey: "title",
        fields: [
            { key: "title", label: "Title", required: true, maxlength: 200, full: true, lang: "si" },
            { key: "content", label: "Content", required: true, full: true, lang: "si" }
        ]
    };

    function renderCategories() { return renderCrud(CRUD_CATEGORIES); }
    function renderGenres() { return renderCrud(CRUD_GENRES); }
    function renderAnnouncements() { return renderCrud(CRUD_ANNOUNCEMENTS); }

    async function renderSettings() {
        pageTitle.textContent = "System Settings";
        content.innerHTML = '<div class="empty-state"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';
        try {
            const res = await api("/api/settings");
            const items = (res && res.data) || [];
            let rows = "";
            items.forEach(function (s) {
                rows += "<tr><td>" + escapeHtml(s.settingKey) + "</td><td>" + escapeHtml(s.settingValue || "—") +
                    "</td><td>" + escapeHtml(s.description || "—") + "</td></tr>";
            });
            content.innerHTML =
                '<div id="formMessage"></div>' +
                '<div class="content-card"><h2>Set / Update a Setting</h2>' +
                '<p class="card-desc">Changes apply to future calculations only — never retroactively.</p>' +
                '<form id="createForm" novalidate><div class="form-grid">' +
                '<div class="form-group"><label>Key <span class="req">*</span></label>' +
                '<input type="text" id="settingKey" maxlength="100" required placeholder="e.g. currency"></div>' +
                '<div class="form-group"><label>Value</label><input type="text" id="settingValue" maxlength="500"></div>' +
                '<div class="form-group full"><label>Description</label><input type="text" id="settingDescription" maxlength="255"></div>' +
                '</div><div class="form-actions"><button type="submit" class="btn btn-primary" id="submitBtn">' +
                '<i class="fas fa-save"></i> Save</button></div></form></div>' +
                '<div class="content-card"><h2>All Settings</h2>' +
                '<div class="table-wrap"><table class="data-table"><thead><tr>' +
                "<th>Key</th><th>Value</th><th>Description</th></tr></thead><tbody>" +
                (rows || '<tr><td colspan="3" class="empty-state">No settings configured yet.</td></tr>') +
                "</tbody></table></div></div>";

            document.getElementById("createForm").onsubmit = async function (e) {
                e.preventDefault();
                const msgEl = document.getElementById("formMessage");
                const key = document.getElementById("settingKey").value.trim();
                if (!key) { msgEl.innerHTML = '<div class="alert alert-error">Key is required.</div>'; return; }
                try {
                    await api("/api/settings", {
                        method: "PUT",
                        body: JSON.stringify({
                            settingKey: key,
                            settingValue: document.getElementById("settingValue").value.trim() || null,
                            description: document.getElementById("settingDescription").value.trim() || null
                        })
                    });
                    navigate("settings");
                } catch (err) {
                    msgEl.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
                }
            };
        } catch (err) {
            content.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
        }
    }

    async function renderRoyaltyAgreements() {
        pageTitle.textContent = "Royalty Agreements";
        content.innerHTML = '<div class="empty-state"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';
        try {
            const res = await api("/api/royalty-agreements");
            const items = (res && res.data) || [];
            let rows = "";
            items.forEach(function (a) {
                rows += "<tr><td>#" + a.royaltyAgreementId + "</td><td>" + escapeHtml(a.agreementNumber) +
                    "</td><td>" + a.authorId + "</td><td>" + a.bookId + "</td><td>" + a.royaltyPercentage +
                    "%</td><td>" + (a.effectiveDate || "—") + "</td><td>" + (a.expiryDate || "open") +
                    '</td><td><span class="badge badge-' + (a.status || "").toLowerCase() + '">' +
                    escapeHtml(a.status) + "</span></td><td>";
                if (a.status === "DRAFT") {
                    rows += '<button type="button" class="btn-icon btn-activate" data-id="' + a.royaltyAgreementId +
                        '" title="Activate"><i class="fas fa-play"></i></button>';
                } else if (a.status === "ACTIVE") {
                    rows += '<button type="button" class="btn-icon btn-expire" data-id="' + a.royaltyAgreementId +
                        '" title="Expire"><i class="fas fa-ban"></i></button>';
                } else {
                    rows += '<span class="muted">—</span>';
                }
                rows += "</td></tr>";
            });
            content.innerHTML =
                '<div id="formMessage"></div>' +
                '<div class="content-card"><h2>New Royalty Agreement</h2>' +
                '<p class="card-desc">Created as DRAFT — activate it once ready. Author/Book ids reference ' +
                'Epic 1/2 records (not owned here).</p>' +
                '<form id="createForm" novalidate><div class="form-grid">' +
                '<div class="form-group"><label>Author ID <span class="req">*</span></label>' +
                '<input type="number" id="authorId" min="1" required></div>' +
                '<div class="form-group"><label>Book ID <span class="req">*</span></label>' +
                '<input type="number" id="bookId" min="1" required></div>' +
                '<div class="form-group"><label>Royalty % <span class="req">*</span></label>' +
                '<input type="number" id="royaltyPercentage" min="0.01" max="100" step="0.01" required></div>' +
                '<div class="form-group"><label>Effective From <span class="req">*</span></label>' +
                '<input type="date" id="effectiveDate" required></div>' +
                '<div class="form-group"><label>Expiry (optional)</label><input type="date" id="expiryDate"></div>' +
                '</div><div class="form-actions"><button type="submit" class="btn btn-primary" id="submitBtn">' +
                '<i class="fas fa-plus"></i> Create</button></div></form></div>' +
                '<div class="content-card"><h2>All Agreements</h2><div id="listMessage"></div>' +
                '<div class="table-wrap"><table class="data-table"><thead><tr>' +
                "<th>ID</th><th>Number</th><th>Author</th><th>Book</th><th>Rate</th><th>From</th><th>To</th>" +
                "<th>Status</th><th>Actions</th></tr></thead><tbody>" +
                (rows || '<tr><td colspan="9" class="empty-state">No royalty agreements yet.</td></tr>') +
                "</tbody></table></div></div>";

            linkDateRange("effectiveDate", "expiryDate", { onChange: function () {
                setFieldError("expiryDate", validateAgreementDates(
                    document.getElementById("effectiveDate").value, document.getElementById("expiryDate").value) === null
                    ? null : (document.getElementById("expiryDate").value ? "Expiry must be after the effective date." : null));
            } });

            document.getElementById("createForm").onsubmit = async function (e) {
                e.preventDefault();
                const msgEl = document.getElementById("formMessage");
                const authorId = document.getElementById("authorId").value;
                const bookId = document.getElementById("bookId").value;
                const royaltyPercentage = document.getElementById("royaltyPercentage").value;
                const effectiveDate = document.getElementById("effectiveDate").value;
                const expiryDate = document.getElementById("expiryDate").value;
                if (!authorId || !bookId || !royaltyPercentage || !effectiveDate) {
                    msgEl.innerHTML = '<div class="alert alert-error">All required fields must be filled.</div>';
                    return;
                }
                const pct = Number(royaltyPercentage);
                if (!(pct > 0 && pct <= 100)) {
                    setFieldError("royaltyPercentage", "Royalty % must be between 0.01 and 100.");
                    return;
                }
                setFieldError("royaltyPercentage", null);
                const dateErr = validateAgreementDates(effectiveDate, expiryDate);
                setFieldError("expiryDate", dateErr);
                if (dateErr) { msgEl.innerHTML = '<div class="alert alert-error">' + escapeHtml(dateErr) + '</div>'; return; }
                try {
                    await api("/api/royalty-agreements", {
                        method: "POST",
                        body: JSON.stringify({
                            authorId: Number(authorId),
                            bookId: Number(bookId),
                            royaltyPercentage: Number(royaltyPercentage),
                            effectiveDate: effectiveDate,
                            expiryDate: expiryDate || null
                        })
                    });
                    navigate("royalty-agreements");
                } catch (err) {
                    msgEl.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
                }
            };
            content.querySelectorAll(".btn-activate").forEach(function (btn) {
                btn.addEventListener("click", async function () {
                    try {
                        await api("/api/royalty-agreements/" + btn.getAttribute("data-id") + "/activate", { method: "POST" });
                        navigate("royalty-agreements");
                    } catch (err) {
                        document.getElementById("listMessage").innerHTML =
                            '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
                    }
                });
            });
            content.querySelectorAll(".btn-expire").forEach(function (btn) {
                btn.addEventListener("click", async function () {
                    try {
                        await api("/api/royalty-agreements/" + btn.getAttribute("data-id") + "/expire", { method: "POST" });
                        navigate("royalty-agreements");
                    } catch (err) {
                        document.getElementById("listMessage").innerHTML =
                            '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
                    }
                });
            });
        } catch (err) {
            content.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
        }
    }

    async function renderRoyaltyCalculations() {
        pageTitle.textContent = "Royalty Calculations";
        content.innerHTML = '<div class="empty-state"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';
        try {
            const agreementsRes = await api("/api/royalty-agreements");
            const agreements = ((agreementsRes && agreementsRes.data) || []).filter(function (a) {
                return a.status === "ACTIVE";
            });
            const calcsRes = await api("/api/royalties");
            const calcs = (calcsRes && calcsRes.data) || [];

            let options = '<option value="">— Select an active agreement —</option>';
            const bookByAgreement = {};
            const agreementById = {};
            agreements.forEach(function (a) {
                bookByAgreement[a.royaltyAgreementId] = a.bookId;
                agreementById[a.royaltyAgreementId] = a;
                options += '<option value="' + a.royaltyAgreementId + '">' + escapeHtml(a.agreementNumber) +
                    " (Author #" + a.authorId + ", Book #" + a.bookId + ", " + a.royaltyPercentage + "%)</option>";
            });

            let rows = "";
            calcs.forEach(function (c) {
                rows += "<tr><td>#" + c.calculationId + "</td><td>" + c.royaltyAgreementId + "</td><td>" +
                    c.salesPeriodStart + " → " + c.salesPeriodEnd + "</td><td>" + c.booksSold + "</td><td>" +
                    money(c.grossSales) + "</td><td>" + money(c.deductions) + "</td><td><strong>" + money(c.royaltyAmount) +
                    '</strong></td><td><span class="badge badge-' + (c.status || "").toLowerCase() + '">' +
                    escapeHtml(c.status) + "</span></td></tr>";
            });

            content.innerHTML =
                '<div id="formMessage"></div>' +
                '<div class="content-card"><h2>Calculate Royalty</h2>' +
                (agreements.length
                    ? '<p class="card-desc">Books sold and gross sales are taken from the <strong>completed</strong> ' +
                      "sales received from Epic 3 for the agreement's book over the period.</p>"
                    : '<div class="alert alert-error">No ACTIVE royalty agreements — activate one on the ' +
                      "Royalty Agreements page first.</div>") +
                '<form id="createForm" novalidate><div class="form-grid">' +
                '<div class="form-group full"><label>Agreement <span class="req">*</span></label>' +
                '<select id="agreementId" required>' + options + '</select></div>' +
                '<div class="form-group"><label>Period Start <span class="req">*</span></label>' +
                '<input type="date" id="periodStart" value="' + monthStartISO(1) + '" required></div>' +
                '<div class="form-group"><label>Period End <span class="req">*</span></label>' +
                '<input type="date" id="periodEnd" value="' + lastDayOfPrevMonthISO() + '" required></div>' +
                '<div class="form-group"><label>Deductions</label>' +
                '<input type="number" id="deductions" min="0" step="0.01" value="0"></div>' +
                '<div class="form-group full"><div id="salesPreview" class="card-desc">Select an agreement and period to preview completed sales.</div></div>' +
                '</div><div class="form-actions"><button type="submit" class="btn btn-primary" id="submitBtn">' +
                '<i class="fas fa-calculator"></i> Calculate</button></div></form></div>' +
                '<div class="content-card"><h2>All Calculations</h2>' +
                '<div class="table-wrap"><table class="data-table"><thead><tr>' +
                "<th>ID</th><th>Agreement</th><th>Period</th><th>Books Sold</th><th>Gross Sales</th>" +
                "<th>Deductions</th><th>Royalty Amount</th><th>Status</th></tr></thead><tbody>" +
                (rows || '<tr><td colspan="8" class="empty-state">No calculations yet.</td></tr>') +
                "</tbody></table></div></div>";

            async function previewSales() {
                const el = document.getElementById("salesPreview");
                const agreementId = document.getElementById("agreementId").value;
                const periodStart = document.getElementById("periodStart").value;
                const periodEnd = document.getElementById("periodEnd").value;
                if (!agreementId || !periodStart || !periodEnd) {
                    el.innerHTML = "Select an agreement and period to preview completed sales.";
                    return;
                }
                const err = validateRoyaltyPeriod(periodStart, periodEnd, agreementById[agreementId]);
                setFieldError("periodEnd", err);
                if (err) { el.innerHTML = '<span style="color:#c53030">' + escapeHtml(err) + "</span>"; return; }
                el.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Looking up completed sales...';
                try {
                    const r = await api("/api/finance/sales/summary?bookId=" + bookByAgreement[agreementId] +
                        "&from=" + periodStart + "&to=" + periodEnd);
                    const sum = (r && r.data) || {};
                    el.innerHTML = "Completed sales for Book #" + sum.bookId + " in this period: <strong>" +
                        sum.completedSales + " sales</strong>, <strong>" + sum.booksSold + " books sold</strong>, gross <strong>" +
                        money(sum.grossSales) + "</strong>" +
                        (sum.booksSold === 0 ? ' <span class="badge badge-cancelled">nothing to calculate</span>' : "");
                } catch (err) {
                    el.innerHTML = '<span style="color:#c53030">' + escapeHtml(err.message) + "</span>";
                }
            }
            ["agreementId", "periodStart", "periodEnd"].forEach(function (id) {
                document.getElementById(id).addEventListener("change", previewSales);
            });
            linkDateRange("periodStart", "periodEnd", { maxToday: true });
            // The period must sit inside the selected agreement's effective dates.
            document.getElementById("agreementId").addEventListener("change", function () {
                const a = agreementById[this.value];
                const ps = document.getElementById("periodStart"), pe = document.getElementById("periodEnd");
                ps.min = a ? a.effectiveDate : "";
                pe.min = ps.value || ps.min;
                if (a && a.expiryDate && a.expiryDate < todayISO()) { ps.max = a.expiryDate; pe.max = a.expiryDate; }
                else { pe.max = todayISO(); ps.max = pe.value || todayISO(); }
            });

            document.getElementById("createForm").onsubmit = async function (e) {
                e.preventDefault();
                const msgEl = document.getElementById("formMessage");
                const agreementId = document.getElementById("agreementId").value;
                const periodStart = document.getElementById("periodStart").value;
                const periodEnd = document.getElementById("periodEnd").value;
                if (!agreementId || !periodStart || !periodEnd) {
                    msgEl.innerHTML = '<div class="alert alert-error">All required fields must be filled.</div>';
                    return;
                }
                const periodErr = validateRoyaltyPeriod(periodStart, periodEnd, agreementById[agreementId]);
                setFieldError("periodEnd", periodErr);
                if (periodErr) { msgEl.innerHTML = '<div class="alert alert-error">' + escapeHtml(periodErr) + '</div>'; return; }
                const ded = Number(document.getElementById("deductions").value || 0);
                if (isNaN(ded) || ded < 0) {
                    setFieldError("deductions", "Deductions cannot be negative.");
                    return;
                }
                setFieldError("deductions", null);
                try {
                    await api("/api/royalties/calculate", {
                        method: "POST",
                        body: JSON.stringify({
                            royaltyAgreementId: Number(agreementId),
                            periodStart: periodStart,
                            periodEnd: periodEnd,
                            deductions: Number(document.getElementById("deductions").value || 0)
                        })
                    });
                    navigate("royalty-calculations");
                } catch (err) {
                    msgEl.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
                }
            };
        } catch (err) {
            content.innerHTML = '<div class="alert alert-error">' + escapeHtml(err.message) + '</div>';
        }
    }

    // Default landing page by role
    if (isAdminRole(user) || isFinanceRole(user)) {
        navigate("epic4-dashboard");
    } else if (isProductionStaff(user) && !isWarehouseStaff(user)) {
        navigate("dashboard");
    } else if (isWarehouseStaff(user) && !isProductionStaff(user)) {
        navigate("wh-dashboard");
    } else {
        navigate("dashboard");
    }
})();

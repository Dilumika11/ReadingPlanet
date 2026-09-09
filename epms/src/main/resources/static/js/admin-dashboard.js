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

    function isStaff(user) {
        return isProductionStaff(user) || isWarehouseStaff(user);
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

    // Default landing page by role
    if (isProductionStaff(user) && !isWarehouseStaff(user)) {
        navigate("dashboard");
    } else if (isWarehouseStaff(user) && !isProductionStaff(user)) {
        navigate("wh-dashboard");
    } else {
        // Admin has both
        navigate("dashboard");
    }
})();

(function () {
    "use strict";

    // CUSTOMER session only — never use rp_admin_* or rp_token
    var TOKEN_KEY = "rp_customer_token";
    var USER_KEY = "rp_customer_user";
    var API = "/api/customer";

    function token() {
        return localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY) || "";
    }

    function user() {
        var raw = localStorage.getItem(USER_KEY) || sessionStorage.getItem(USER_KEY);
        if (!raw) return null;
        try { return JSON.parse(raw); } catch (e) { return null; }
    }

    function authHeaders() {
        var t = token();
        return {
            "Content-Type": "application/json",
            "Authorization": t ? ("Bearer " + t) : ""
        };
    }

    function requireLogin() {
        var t = token();
        var u = user();
        var role = u && u.role;
        if (role && typeof role === "object") role = role.name || "";
        role = String(role || "").toUpperCase().replace(/^ROLE_/, "");
        if (!t || !u || (role !== "CUSTOMER" && role !== "AUTHOR")) {
            window.location.replace("/customer-login.html");
            return false;
        }
        return true;
    }

    function showMsg(text, type) {
        var el = document.getElementById("accountMsg");
        if (!el) return;
        el.hidden = false;
        el.textContent = text || "";
        el.className = "account-msg " + (type || "");
    }

    function hideMsg() {
        var el = document.getElementById("accountMsg");
        if (!el) return;
        el.hidden = true;
        el.textContent = "";
    }

    function switchPanel(id) {
        document.querySelectorAll(".account-panel").forEach(function (p) {
            p.classList.toggle("active", p.id === "panel-" + id);
        });
        document.querySelectorAll(".account-nav").forEach(function (b) {
            b.classList.toggle("active", b.getAttribute("data-panel") === id);
        });
        if (id === "orders") loadOrders();
        if (id === "activity") loadActivity();
    }

    function loadProfile() {
        fetch(API + "/profile", { headers: authHeaders() })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (!res.success || !res.data) {
                    showMsg(res.message || "Could not load profile", "error");
                    return;
                }
                var d = res.data;
                document.getElementById("profEmail").value = d.email || "";
                document.getElementById("profUsername").value = d.username || "";
                document.getElementById("profFirst").value = d.firstName || "";
                document.getElementById("profLast").value = d.lastName || "";
                document.getElementById("profPhone").value = d.phoneNumber || "";
                var u = user() || {};
                u.fullName = d.fullName || ((d.firstName || "") + " " + (d.lastName || "")).trim();
                u.email = d.email;
                u.role = "CUSTOMER";
                localStorage.setItem(USER_KEY, JSON.stringify(u));
                sessionStorage.setItem(USER_KEY, JSON.stringify(u));
            })
            .catch(function () {
                showMsg("Network error loading profile", "error");
            });
    }

    function esc(v) {
        return String(v == null ? "" : v).replace(/&/g, "&amp;").replace(/</g, "&lt;")
            .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }

    function formatMoney(n) {
        return "Rs. " + (n != null ? Number(n).toFixed(2) : "0.00");
    }

    function formatDate(d) {
        if (!d) return "-";
        return String(d).replace("T", " ").substring(0, 16);
    }

    function canCancel(status) {
        var s = String(status || "").toUpperCase();
        return s === "CONFIRMED" || s === "PACKED";
    }

    function statusBadgeClass(status) {
        var s = String(status || "").toUpperCase();
        if (s === "PENDING" || s === "PENDING_APPROVAL") return "status-pending";
        if (s === "CONFIRMED" || s === "APPROVED") return "status-confirmed";
        if (s === "PACKED") return "status-packed";
        if (s === "DISPATCHED" || s === "SHIPPED") return "status-dispatched";
        if (s === "DELIVERED" || s === "COMPLETED") return "status-delivered";
        if (s === "CANCELLED" || s === "CANCELED") return "status-cancelled";
        return "status-other";
    }

    function statusLabel(status) {
        var s = String(status || "—").toUpperCase().replace(/_/g, " ");
        return s.charAt(0) + s.slice(1).toLowerCase();
    }

    /** Delivery pipeline steps for retail orders */
    var TRACK_STEPS = ["CONFIRMED", "PACKED", "DISPATCHED", "DELIVERED"];

    function stepIndex(status) {
        var s = String(status || "").toUpperCase();
        if (s === "APPROVED") s = "CONFIRMED";
        if (s === "SHIPPED") s = "DISPATCHED";
        if (s === "COMPLETED") s = "DELIVERED";
        if (s === "CANCELLED" || s === "CANCELED") return -1;
        var i = TRACK_STEPS.indexOf(s);
        return i;
    }

    function renderTrackTimeline(status) {
        var idx = stepIndex(status);
        if (idx < 0) {
            return '<div class="order-track cancelled-track">Order cancelled. There will be no further delivery updates.</div>';
        }
        var html = '<ol class="order-track">';
        TRACK_STEPS.forEach(function (step, i) {
            var cls = "track-step";
            if (i < idx) cls += " done";
            else if (i === idx) cls += " current";
            else cls += " upcoming";
            html += '<li class="' + cls + '"><span class="track-dot"></span><span class="track-label">' +
                statusLabel(step) + "</span></li>";
        });
        html += "</ol>";
        return html;
    }

    function loadOrders() {
        var box = document.getElementById("ordersList");
        if (!box) return;
        box.innerHTML = '<div class="empty">Loading…</div>';
        fetch(API + "/orders", { headers: authHeaders() })
            .then(function (r) {
                if (r.status === 401) {
                    window.location.replace("/customer-login.html");
                    return Promise.reject(new Error("Unauthorized"));
                }
                if (!r.ok) {
                    return r.json().then(function (body) {
                        throw new Error((body && body.message) || ("HTTP " + r.status));
                    }).catch(function (e) {
                        if (e.message) throw e;
                        throw new Error("HTTP " + r.status);
                    });
                }
                return r;
            })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (res && res.success === false) {
                    box.innerHTML = '<div class="empty">' + (res.message || "Could not load orders. Please log in again.") + "</div>";
                    return;
                }
                var list = (res && res.data) || [];
                if (!list.length) {
                    box.innerHTML = '<div class="empty">You have no orders yet. Place an order from the store while logged in as a customer.</div>';
                    return;
                }
                var html = "";
                list.forEach(function (view) {
                    var o = view.order || {};
                    var id = o.customerOrderId;
                    var st = o.orderStatus || "—";
                    var badge = '<span class="status-badge ' + statusBadgeClass(st) + '">' +
                        statusLabel(st) + "</span>";
                    var action = canCancel(st)
                        ? '<button type="button" class="btn-cancel-order" data-id="' + id + '">Cancel</button>'
                        : "";
                    html += '<div class="order-card" data-order-id="' + id + '">';
                    html += '<div class="order-card-head">';
                    html += '<div><strong>' + esc(o.orderNumber || id) + '</strong>';
                    html += '<span class="order-meta">' + formatDate(o.orderDate || o.createdAt) + "</span></div>";
                    html += '<div class="order-head-right">' + badge + " " + action + "</div>";
                    html += "</div>";
                    html += '<div class="order-card-body">';
                    html += '<div class="order-total">Total: ' + formatMoney(o.totalAmount) + "</div>";
                    (view.items || []).forEach(function (i) {
                        html += '<div class="order-meta">' + esc(i.title) + " x " + i.quantity +
                            " (" + formatMoney(i.subtotal) + ")</div>";
                    });
                    html += '<div class="order-meta">Deliver to: ' + esc(o.recipientName) + ", " + esc(o.shippingAddress) + "</div>";
                    if (view.shipment && view.shipment.trackingNumber) {
                        html += '<div class="order-meta">Courier: ' + esc(view.shipment.shippingProvider) +
                            ", tracking number " + esc(view.shipment.trackingNumber) + "</div>";
                    }
                    if (o.cancelReason) {
                        html += '<div class="order-meta">Reason: ' + esc(o.cancelReason) + "</div>";
                    }
                    html += '<div class="order-track-wrap"><p class="track-title">Delivery tracking</p>';
                    html += renderTrackTimeline(st);
                    html += "</div></div></div>";
                });
                box.innerHTML = html;
                box.querySelectorAll(".btn-cancel-order").forEach(function (btn) {
                    btn.addEventListener("click", function () {
                        var orderId = btn.getAttribute("data-id");
                        if (!confirm("Cancel this order?")) return;
                        cancelOrder(orderId);
                    });
                });
            })
            .catch(function (err) {
                box.innerHTML = '<div class="empty">Could not load orders' +
                    (err && err.message ? (": " + err.message) : ".") +
                    " Make sure you are logged in as a customer and placed an order.</div>";
            });
    }

    function cancelOrder(orderId) {
        hideMsg();
        fetch(API + "/orders/" + orderId + "/cancel", {
            method: "POST",
            headers: authHeaders(),
            body: JSON.stringify({ reason: "Cancelled by the customer" })
        })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (!res.success) {
                    showMsg(res.message || "Could not cancel order", "error");
                    return;
                }
                showMsg("Order cancelled.", "success");
                loadOrders();
            })
            .catch(function () {
                showMsg("Network error while cancelling", "error");
            });
    }

    function loadActivity() {
        var box = document.getElementById("activityList");
        if (!box) return;
        box.innerHTML = '<div class="empty">Loading…</div>';
        fetch("/api/me/login-activity", { headers: authHeaders() })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                var list = (res && res.data) || [];
                if (!list.length) {
                    box.innerHTML = '<div class="empty">No login activity recorded yet.</div>';
                    return;
                }
                var html = '<table class="account-table"><thead><tr>' +
                    "<th>When</th><th>IP</th><th>Device / Browser</th><th>Active</th></tr></thead><tbody>";
                list.forEach(function (s) {
                    html += "<tr><td>" + formatDate(s.loginAt) +
                        "</td><td>" + esc(s.ipAddress || "-") +
                        "</td><td>" + esc(s.userAgent ? String(s.userAgent).substring(0, 80) : "-") +
                        "</td><td>" + (s.isActive ? "Yes" : "No") + "</td></tr>";
                });
                html += "</tbody></table>";
                box.innerHTML = html;
            })
            .catch(function () {
                box.innerHTML = '<div class="empty">Could not load login activity.</div>';
            });
    }

    if (!requireLogin()) return;

    document.querySelectorAll(".account-nav").forEach(function (btn) {
        btn.addEventListener("click", function () {
            switchPanel(btn.getAttribute("data-panel"));
        });
    });

    var form = document.getElementById("profileForm");
    if (form) {
        form.addEventListener("submit", function (e) {
            e.preventDefault();
            hideMsg();
            fetch(API + "/profile", {
                method: "PUT",
                headers: authHeaders(),
                body: JSON.stringify({
                    firstName: document.getElementById("profFirst").value.trim(),
                    lastName: document.getElementById("profLast").value.trim(),
                    phoneNumber: document.getElementById("profPhone").value.trim()
                })
            })
                .then(function (r) { return r.json(); })
                .then(function (res) {
                    if (!res.success) {
                        showMsg(res.message || "Update failed", "error");
                        return;
                    }
                    showMsg("Profile updated.", "success");
                    if (res.data) {
                        var u = user() || {};
                        u.fullName = res.data.fullName;
                        localStorage.setItem(USER_KEY, JSON.stringify(u));
                        sessionStorage.setItem(USER_KEY, JSON.stringify(u));
                    }
                })
                .catch(function () {
                    showMsg("Network error", "error");
                });
        });
    }

    loadProfile();
})();

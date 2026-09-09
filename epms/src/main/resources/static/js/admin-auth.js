(function () {
    "use strict";

    const API_BASE = "/api/auth";
    const STAFF_ROLES = [
        "ADMIN",
        "PRODUCTION_MANAGER",
        "EDITOR",
        "PROOFREADER",
        "DESIGNER",
        "INVENTORY_STAFF",
        "SALES_STAFF",
        "FINANCE_STAFF"
    ];

    // Password toggle
    document.querySelectorAll(".toggle-password").forEach(function (btn) {
        btn.addEventListener("click", function () {
            const input = document.getElementById(btn.getAttribute("data-target"));
            if (!input) return;
            const icon = btn.querySelector("i");
            if (input.type === "password") {
                input.type = "text";
                icon.classList.remove("fa-eye");
                icon.classList.add("fa-eye-slash");
            } else {
                input.type = "password";
                icon.classList.remove("fa-eye-slash");
                icon.classList.add("fa-eye");
            }
        });
    });

    function showMessage(el, text, type) {
        el.hidden = false;
        el.textContent = text;
        el.className = "auth-message " + type;
    }

    function hideMessage(el) {
        el.hidden = true;
        el.textContent = "";
        el.className = "auth-message";
    }

    function isStaffRole(roles) {
        if (!roles || !roles.length) return false;
        return roles.some(function (r) {
            return STAFF_ROLES.indexOf(r) !== -1;
        });
    }

    function dashboardPathForRoles(roles) {
        // All staff currently share the same dashboard shell;
        // navigation is filtered by role inside the dashboard.
        return "/admin/dashboard.html";
    }

    // If already logged in as staff, redirect away from login
    (function checkExistingSession() {
        const token = localStorage.getItem("rp_token") || sessionStorage.getItem("rp_token");
        const userRaw = localStorage.getItem("rp_user") || sessionStorage.getItem("rp_user");
        if (!token || !userRaw) return;
        try {
            const user = JSON.parse(userRaw);
            if (user.roles && isStaffRole(user.roles)) {
                window.location.replace(dashboardPathForRoles(user.roles));
            }
        } catch (e) { /* ignore */ }
    })();

    const form = document.getElementById("staffLoginForm");
    const loginBtn = document.getElementById("loginBtn");
    const messageEl = document.getElementById("login-message");

    form.addEventListener("submit", async function (e) {
        e.preventDefault();
        hideMessage(messageEl);

        const email = document.getElementById("loginEmail").value.trim();
        const password = document.getElementById("loginPassword").value;

        if (!email) {
            showMessage(messageEl, "Email is required", "error");
            return;
        }
        if (!password) {
            showMessage(messageEl, "Password is required", "error");
            return;
        }

        loginBtn.disabled = true;
        loginBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Logging in...';

        try {
            const res = await fetch(API_BASE + "/login", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ email: email, password: password })
            });

            const data = await res.json();

            if (data.success && data.data && data.data.token) {
                const roles = data.data.role ? [data.data.role] : (data.data.roles || []);
                if (!isStaffRole(roles)) {
                    showMessage(messageEl, "This portal is for staff only. Customers should use the main website login.", "error");
                    return;
                }

                // Persist session (staff sessions use localStorage)
                localStorage.setItem("rp_token", data.data.token);
                localStorage.setItem("rp_user", JSON.stringify({
                    userId: data.data.userId,
                    email: data.data.email,
                    fullName: data.data.fullName,
                    roles: roles
                }));

                showMessage(messageEl, "Login successful. Redirecting...", "success");
                setTimeout(function () {
                    window.location.href = dashboardPathForRoles(roles);
                }, 600);
            } else {
                showMessage(messageEl, data.message || "Invalid email or password.", "error");
            }
        } catch (err) {
            showMessage(messageEl, "Could not connect to server. Please try again.", "error");
        } finally {
            loginBtn.disabled = false;
            loginBtn.innerHTML = '<i class="fas fa-sign-in-alt"></i> Login';
        }
    });
})();

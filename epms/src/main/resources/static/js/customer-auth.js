/**
 * Customer-only auth. NEVER redirects to /author/*
 * Session keys: rp_customer_token, rp_customer_user
 */
(function () {
    "use strict";

    var API = "/api/auth";
    var TOKEN_KEY = "rp_customer_token";
    var USER_KEY = "rp_customer_user";

    function $(id) { return document.getElementById(id); }

    function showMsg(el, text, type) {
        if (!el) return;
        el.textContent = text || "";
        el.className = "c-msg show " + (type || "");
    }

    function hideMsg(el) {
        if (!el) return;
        el.textContent = "";
        el.className = "c-msg";
    }

    function roleOf(data) {
        if (!data) return null;
        var r = data.role;
        if (r && typeof r === "object") r = r.name || r.role || r.value || "";
        if (r) return String(r).toUpperCase().replace(/^ROLE_/, "");
        if (data.roles && data.roles.length) {
            return String(data.roles[0]).toUpperCase().replace(/^ROLE_/, "");
        }
        return null;
    }

    function saveCustomerSession(data) {
        var payload = {
            userId: data.userId,
            fullName: data.fullName,
            email: data.email,
            role: "CUSTOMER"
        };
        localStorage.setItem(TOKEN_KEY, data.token);
        localStorage.setItem(USER_KEY, JSON.stringify(payload));
        sessionStorage.setItem(TOKEN_KEY, data.token);
        sessionStorage.setItem(USER_KEY, JSON.stringify(payload));
    }

    /** Moves a guest cart into the account, then leaves for the home page. */
    function goHome() {
        var merge = window.RpCart ? RpCart.syncToServer() : Promise.resolve();
        Promise.resolve(merge).catch(function () {}).then(function () { window.location.replace("/"); });
    }

    // Clear accidental author redirects: if someone landed here with author session, ignore it
    // Do not clear author keys — just don't use them

    var p = new URLSearchParams(window.location.search);
    if (p.get("googleError")) {
        showMsg($("loginMsg"), p.get("googleError"), "error");
    }

    var loginForm = $("loginForm");
    var regForm = $("regForm");
    if (!loginForm || !regForm) {
        console.error("Customer login forms not found in DOM");
        return;
    }

    loginForm.addEventListener("submit", function (e) {
        e.preventDefault();
        hideMsg($("loginMsg"));
        var email = ($("loginEmail").value || "").trim();
        var password = $("loginPassword").value || "";
        $("loginBtn").disabled = true;
        fetch(API + "/store-login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email: email, password: password })
        })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                $("loginBtn").disabled = false;
                if (!res.success) {
                    showMsg($("loginMsg"), res.message || "Login failed", "error");
                    return;
                }
                var role = roleOf(res.data);
                if (role !== "CUSTOMER") {
                    showMsg($("loginMsg"),
                        "Account role is " + (role || "unknown") + ", not CUSTOMER. " +
                        "Use Getting Published for Author login.",
                        "error");
                    return;
                }
                saveCustomerSession(res.data);
                // Homepage only, never the author portal
                goHome();
            })
            .catch(function () {
                $("loginBtn").disabled = false;
                showMsg($("loginMsg"), "Network error", "error");
            });
    });

    regForm.addEventListener("submit", function (e) {
        e.preventDefault();
        hideMsg($("regMsg"));
        var body = {
            username: ($("regUsername").value || "").trim(),
            firstName: ($("regFirst").value || "").trim(),
            lastName: ($("regLast").value || "").trim(),
            email: ($("regEmail").value || "").trim(),
            password: $("regPassword").value || "",
            role: "CUSTOMER"
        };
        $("regBtn").disabled = true;
        fetch(API + "/register", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body)
        })
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (!res.success) {
                    $("regBtn").disabled = false;
                    showMsg($("regMsg"), res.message || "Registration failed", "error");
                    return null;
                }
                return fetch(API + "/store-login", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ email: body.email, password: body.password })
                }).then(function (r2) { return r2.json(); });
            })
            .then(function (loginRes) {
                $("regBtn").disabled = false;
                if (!loginRes) return;
                if (!loginRes.success) {
                    showMsg($("regMsg"), "Registered. Please login with your email/password.", "success");
                    return;
                }
                var role = roleOf(loginRes.data);
                if (role !== "CUSTOMER") {
                    showMsg($("regMsg"),
                        "Registered but role is " + role + ". Expected CUSTOMER. Check users.role in DB.",
                        "error");
                    return;
                }
                saveCustomerSession(loginRes.data);
                goHome();
            })
            .catch(function () {
                $("regBtn").disabled = false;
                showMsg($("regMsg"), "Network error", "error");
            });
    });

    var showForgot = $("showForgot");
    if (showForgot) {
        showForgot.addEventListener("click", function (e) {
            e.preventDefault();
            $("forgotBox").classList.toggle("open");
        });
    }

    var forgotBtn = $("forgotBtn");
    if (forgotBtn) {
        forgotBtn.addEventListener("click", function () {
            hideMsg($("forgotMsg"));
            var email = ($("forgotEmail").value || "").trim();
            if (!email) {
                showMsg($("forgotMsg"), "Enter email", "error");
                return;
            }
            fetch(API + "/forgot-password", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ email: email })
            })
                .then(function (r) { return r.json(); })
                .then(function (res) {
                    showMsg($("forgotMsg"), res.message || "Done", res.success ? "success" : "error");
                })
                .catch(function () {
                    showMsg($("forgotMsg"), "Network error", "error");
                });
        });
    }

    var googleBtn = $("googleLoginBtn");
    if (googleBtn) {
        // Only offer Google sign-in when the server has a Google client configured
        googleBtn.style.setProperty("display", "none", "important");
        fetch(API + "/oauth2/enabled").then(function (r) { return r.json(); }).then(function (res) {
            if (res && res.data && res.data.enabled) googleBtn.style.removeProperty("display");
        }).catch(function () {});
        googleBtn.addEventListener("click", function () {
            window.location.href = API + "/oauth2/start?portal=customer";
        });
    }
})();

/**
 * Reading Planet shared header.
 * Public pages keep the normal store header.
 * Author pages use the same Reading Planet design with author navigation.
 */
(function () {
    "use strict";

    
    function getCustomerUser() {
        const raw = localStorage.getItem("rp_customer_user") || sessionStorage.getItem("rp_customer_user");
        if (!raw) return null;
        try { return JSON.parse(raw); } catch (e) { return null; }
    }

    function updatePublicAccountButton() {
        const btn = document.querySelector(".public-account-btn span");
        const link = document.querySelector(".public-account-btn");
        if (!btn || !link) return;
        const customer = getCustomerUser();
        const token = localStorage.getItem("rp_customer_token") || sessionStorage.getItem("rp_customer_token");
        if (customer && token && String(customer.role || "").toUpperCase() === "CUSTOMER") {
            btn.textContent = customer.fullName || customer.email || "My Account";
            link.href = "/customer-login.html";
            link.title = "Customer: " + (customer.email || "");
        } else {
            btn.textContent = "Account";
            link.href = "/customer-login.html";
        }
    }

    
    function getCustomerToken() {
        return localStorage.getItem("rp_customer_token") || sessionStorage.getItem("rp_customer_token");
    }

    function getCustomerUser() {
        const raw = localStorage.getItem("rp_customer_user") || sessionStorage.getItem("rp_customer_user");
        if (!raw) return null;
        try { return JSON.parse(raw); } catch (e) { return null; }
    }

    function clearCustomerSession() {
        ["rp_customer_token", "rp_customer_user"].forEach(function (k) {
            localStorage.removeItem(k);
            sessionStorage.removeItem(k);
        });
    }

    function setupPublicAccountMenu() {
        const btn = document.getElementById("publicAccountBtn");
        const label = document.getElementById("publicAccountLabel");
        const menu = document.getElementById("publicAccountMenu");
        const logoutBtn = document.getElementById("menuLogout");
        const wrap = document.getElementById("publicAccountWrap");
        if (!btn || !label) return;

        const token = getCustomerToken();
        const user = getCustomerUser();
        const loggedIn = !!(token && user && String(user.role || "").toUpperCase() === "CUSTOMER");

        if (loggedIn) {
            label.textContent = user.fullName || user.email || "My Account";
            btn.href = "#";
            btn.setAttribute("aria-haspopup", "true");
            if (menu) {
                btn.onclick = function (e) {
                    e.preventDefault();
                    const open = !menu.hidden;
                    menu.hidden = open;
                    btn.classList.toggle("open", !open);
                };
            }
            if (logoutBtn) {
                logoutBtn.onclick = function () {
                    clearCustomerSession();
                    window.location.replace("/");
                };
            }
            document.addEventListener("click", function (e) {
                if (!wrap || !menu) return;
                if (!wrap.contains(e.target)) {
                    menu.hidden = true;
                    btn.classList.remove("open");
                }
            });
        } else {
            label.textContent = "Account";
            btn.href = "/customer-login.html";
            btn.onclick = null;
            if (menu) menu.hidden = true;
        }
    }

    function getStoredUser() {
        const raw = localStorage.getItem("rp_user") || sessionStorage.getItem("rp_user");
        if (!raw) return null;
        try { return JSON.parse(raw); } catch (e) { return null; }
    }

    function getToken() {
        return localStorage.getItem("rp_token") || sessionStorage.getItem("rp_token");
    }

    function clearSession() {
        ["rp_token", "rp_user"].forEach(function (key) {
            localStorage.removeItem(key);
            sessionStorage.removeItem(key);
        });
    }

    function isAuthorPage() {
        const body = document.body;
        if (!body) return false;
        return !body.classList.contains("author-login-page") &&
            (body.classList.contains("author-body") ||
             body.classList.contains("author-profile-page") ||
             body.classList.contains("author-page"));
    }

    function authorDisplayName(user) {
        if (!user) return "Author";
        return user.fullName || user.name || user.username || user.email || "Author";
    }

    function setAuthorName(name) {
        const display = name || "Author";
        const header = document.getElementById("authorHeaderName");
        const welcome = document.getElementById("welcomeName");
        const legacyHeader = document.getElementById("headerUserName");
        if (header) header.textContent = display;
        if (welcome) welcome.textContent = display;
        if (legacyHeader) legacyHeader.textContent = display;
    }

    function markAuthorNav() {
        if (!isAuthorPage()) return;
        const path = window.location.pathname;
        document.querySelectorAll("[data-author-nav]").forEach(function (link) {
            link.classList.remove("active");
        });
        let key = "dashboard";
        if (path.includes("my-submissions") || path.includes("my-submission-details")) key = "submissions";
        else if (path.includes("submit-manuscript")) key = "submit";
        else if (path.includes("profile")) key = "profile";
        const status = new URLSearchParams(window.location.search).get("status");
        if (path.includes("my-submissions") && status === "UNDER_REVIEW") key = "reviews";
        const active = document.querySelector('[data-author-nav="' + key + '"]');
        if (active) active.classList.add("active");
    }

    async function loadAuthorName() {
        const stored = getStoredUser();
        setAuthorName(authorDisplayName(stored));

        const token = getToken();
        if (!token || !isAuthorPage()) return;

        try {
            const response = await fetch("/api/author/profile", {
                headers: { "Authorization": "Bearer " + token }
            });
            if (!response.ok) return;
            const result = await response.json();
            if (!result.success || !result.data) return;
            const profile = result.data;
            const name = profile.fullName || [profile.firstName, profile.lastName].filter(Boolean).join(" ") || profile.penName || authorDisplayName(stored);
            setAuthorName(name);

            const updated = Object.assign({}, stored || {}, {
                fullName: name,
                email: profile.email || (stored && stored.email),
                role: "AUTHOR"
            });
            localStorage.setItem("rp_user", JSON.stringify(updated));
        } catch (e) {
            console.warn("Unable to load author header profile.", e);
        }
    }

    function initHeader() {
        const searchForm = document.getElementById("searchForm");
        const searchInput = document.getElementById("searchInput");
        const wishlistBtn = document.getElementById("wishlistBtn");
        const cartBtn = document.getElementById("cartBtn");
        const wishlistBadge = document.getElementById("wishlistBadge");
        const cartBadge = document.getElementById("cartBadge");
        const logoutBtn = document.getElementById("authorLogoutBtn");

        if (searchForm && searchInput) {
            searchForm.addEventListener("submit", function (e) {
                e.preventDefault();
                const q = searchInput.value.trim();
                if (q) console.log("Searching for:", q);
            });
        }

        if (wishlistBtn) wishlistBtn.addEventListener("click", function () {});
        if (cartBtn) {
            cartBtn.addEventListener("click", function (e) {
                e.preventDefault();
                if (window.RpCartDrawer && typeof window.RpCartDrawer.open === "function") {
                    window.RpCartDrawer.open();
                } else {
                    window.location.href = "/cart.html";
                }
            });
        }
        if (window.RpCart && typeof window.RpCart.refreshBadge === "function") {
            window.RpCart.refreshBadge();
        } else if (cartBadge) {
            try {
                var cart = JSON.parse(localStorage.getItem("rp_cart") || "[]");
                var count = Array.isArray(cart)
                    ? cart.reduce(function (s, i) { return s + (parseInt(i.qty || i.quantity, 10) || 0); }, 0)
                    : 0;
                cartBadge.textContent = String(count);
            } catch (err) {
                cartBadge.textContent = "0";
            }
        }

        window.updateWishlistCount = function (count) {
            if (wishlistBadge) wishlistBadge.textContent = count;
        };
        window.updateCartCount = function (count) {
            if (cartBadge) cartBadge.textContent = count;
        };

        if (logoutBtn) {
            logoutBtn.addEventListener("click", function () {
                clearSession();
                window.location.replace("/");
            });
        }

        markAuthorNav();
        loadAuthorName();
        setupPublicAccountMenu();
    }

    var _origInitHeader = initHeader;
    function initHeaderWrapped() {
        _origInitHeader.apply(this, arguments);
        try { document.dispatchEvent(new CustomEvent("rp-header-ready")); } catch (e) {}
    }
    window.initReadingPlanetHeader = initHeaderWrapped;
})();

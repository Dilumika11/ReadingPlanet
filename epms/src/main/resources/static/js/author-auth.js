/**
 * Author Portal – Login + Register
 * Login:  POST /api/auth/login  (AUTHOR only)
 * Register: POST /api/auth/register with role AUTHOR
 * Storage: rp_token, rp_user
 */
(function () {
    "use strict";

    const API_BASE = "/api/auth";
    const AUTHOR_ROLE = "AUTHOR";
    const DASHBOARD_PATH = "/author/dashboard.html";

    // ---------- helpers ----------
    document.querySelectorAll(".toggle-password").forEach(function (btn) {
        btn.addEventListener("click", function () {
            const input = document.getElementById(btn.getAttribute("data-target"));
            if (!input) return;

            const icon = btn.querySelector("i");

            if (input.type === "password") {
                input.type = "text";

                if (icon) {
                    icon.classList.remove("fa-eye");
                    icon.classList.add("fa-eye-slash");
                }

            } else {
                input.type = "password";

                if (icon) {
                    icon.classList.remove("fa-eye-slash");
                    icon.classList.add("fa-eye");
                }
            }
        });
    });

    function showMessage(el, text, type) {
        if (!el) return;

        el.hidden = false;
        el.textContent = text;
        el.className = "auth-message " + type;
    }

    function hideMessage(el) {
        if (!el) return;

        el.hidden = true;
        el.textContent = "";
        el.className = "auth-message";
    }

    function extractRole(data) {
        if (!data) return null;

        if (data.role) {
            return String(data.role).toUpperCase();
        }

        if (Array.isArray(data.roles) && data.roles.length) {
            return String(data.roles[0]).toUpperCase();
        }

        return null;
    }

    function isAuthorRole(role) {
        if (!role) return false;
        role = String(role).toUpperCase().replace(/^ROLE_/, "");
        return role === AUTHOR_ROLE;
    }

    function isValidEmail(email) {
        return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
    }

    // Redirect if already logged in as AUTHOR
    (function checkExistingSession() {

        const token =
            localStorage.getItem("rp_token") ||
            sessionStorage.getItem("rp_token");

        const userRaw =
            localStorage.getItem("rp_user") ||
            sessionStorage.getItem("rp_user");

        if (!token || !userRaw) return;

        try {

            const user = JSON.parse(userRaw);

            const role = user.role
                ? String(user.role).toUpperCase()
                : (
                    user.roles && user.roles[0]
                        ? String(user.roles[0]).toUpperCase()
                        : null
                );

            if (isAuthorRole(role)) {
                window.location.replace(DASHBOARD_PATH);
            }

        } catch (e) {
            /* ignore */
        }

    })();

    // Smooth scroll between columns on mobile
    var scrollToRegister =
        document.getElementById("scrollToRegister");

    var scrollToLogin =
        document.getElementById("scrollToLogin");

    if (scrollToRegister) {

        scrollToRegister.addEventListener("click", function (e) {

            e.preventDefault();

            var target =
                document.getElementById("register-heading");

            if (target) {
                target.scrollIntoView({
                    behavior: "smooth",
                    block: "start"
                });
            }

        });
    }

    if (scrollToLogin) {

        scrollToLogin.addEventListener("click", function (e) {

            e.preventDefault();

            var target =
                document.getElementById("login-heading");

            if (target) {
                target.scrollIntoView({
                    behavior: "smooth",
                    block: "start"
                });
            }

        });
    }

    // ---------- LOGIN ----------
    var loginForm =
        document.getElementById("authorLoginForm");

    var loginBtn =
        document.getElementById("loginBtn");

    var loginMessageEl =
        document.getElementById("login-message");

    var googleBtn =
        document.getElementById("googleLoginBtn");

    var forgotLink =
        document.getElementById("forgotPasswordLink");

    if (loginForm) {

        loginForm.addEventListener("submit", async function (e) {

            e.preventDefault();

            hideMessage(loginMessageEl);

            var email =
                document.getElementById("loginEmail").value.trim();

            var password =
                document.getElementById("loginPassword").value;

            if (!email) {

                showMessage(
                    loginMessageEl,
                    "Email is required",
                    "error"
                );

                return;
            }

            if (!password) {

                showMessage(
                    loginMessageEl,
                    "Password is required",
                    "error"
                );

                return;
            }

            loginBtn.disabled = true;
            loginBtn.textContent = "Logging in...";

            try {

                var res = await fetch(
                    API_BASE + "/login",
                    {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/json"
                        },
                        body: JSON.stringify({
                            email: email,
                            password: password
                        })
                    }
                );

                var data = await res.json();

                if (
                    data.success &&
                    data.data &&
                    data.data.token
                ) {

                    var role =
                        extractRole(data.data);

                    if (!isAuthorRole(role)) {

                        showMessage(
                            loginMessageEl,
                            "This account is not an Author account.",
                            "error"
                        );

                        return;
                    }

                    localStorage.setItem(
                        "rp_token",
                        data.data.token
                    );

                    localStorage.setItem(
                        "rp_user",
                        JSON.stringify({
                            userId: data.data.userId,
                            email: data.data.email,
                            fullName: data.data.fullName,
                            role: role,
                            roles: [role]
                        })
                    );

                    showMessage(
                        loginMessageEl,
                        "Login successful. Redirecting...",
                        "success"
                    );

                    setTimeout(function () {

                        window.location.href =
                            DASHBOARD_PATH;

                    }, 500);

                } else {

                    showMessage(
                        loginMessageEl,
                        data.message ||
                        "Invalid email or password.",
                        "error"
                    );
                }

            } catch (err) {

                showMessage(
                    loginMessageEl,
                    "Could not connect to server. Please try again.",
                    "error"
                );

            } finally {

                loginBtn.disabled = false;
                loginBtn.textContent = "Login";
            }
        });
    }

    // ---------- GOOGLE LOGIN ----------
    if (googleBtn) {

        var googleError = new URLSearchParams(window.location.search).get("googleError");
        if (googleError && typeof loginMessageEl !== "undefined" && loginMessageEl) {
            showMessage(loginMessageEl, googleError, "error");
        }

        // Only offer Google sign-in when the server has a Google client configured
        googleBtn.style.setProperty("display", "none", "important");
        fetch(API_BASE + "/oauth2/enabled")
            .then(function (r) { return r.json(); })
            .then(function (res) {
                if (res && res.data && res.data.enabled) googleBtn.style.removeProperty("display");
            })
            .catch(function () {});

        googleBtn.addEventListener("click", function () {

            // Start Google sign-in for the author portal
            window.location.href =
                API_BASE + "/oauth2/start?portal=author";

        });
    }

    // ---------- FORGOT PASSWORD ----------
    if (forgotLink) {

        forgotLink.addEventListener("click", async function (e) {

            e.preventDefault();

            hideMessage(loginMessageEl);

            // Use the email already entered in the Login form.
            // If it is empty, ask the author for the email address.
            var loginEmailInput =
                document.getElementById("loginEmail");

            var email = loginEmailInput
                ? loginEmailInput.value.trim()
                : "";

            if (!email) {

                email = window.prompt(
                    "Enter your registered email address:"
                );

                if (email === null) {
                    return;
                }

                email = email.trim();
            }

            if (!email) {

                showMessage(
                    loginMessageEl,
                    "Email is required.",
                    "error"
                );

                return;
            }

            if (!isValidEmail(email)) {

                showMessage(
                    loginMessageEl,
                    "Please enter a valid email address.",
                    "error"
                );

                return;
            }

            if (loginEmailInput) {
                loginEmailInput.value = email;
            }

            if (forgotLink) {
                forgotLink.style.pointerEvents = "none";
                forgotLink.style.opacity = "0.65";
            }

            try {

                var res = await fetch(
                    API_BASE + "/forgot-password",
                    {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/json"
                        },
                        body: JSON.stringify({
                            email: email
                        })
                    }
                );

                var data = await res.json();

                if (res.ok && data.success) {

                    showMessage(
                        loginMessageEl,
                        data.message ||
                        "If an account exists with this email, a password reset link has been sent.",
                        "success"
                    );

                } else {

                    showMessage(
                        loginMessageEl,
                        data.message ||
                        "Unable to send the password reset email. Please try again.",
                        "error"
                    );
                }

            } catch (err) {

                console.error(
                    "Forgot password error:",
                    err
                );

                showMessage(
                    loginMessageEl,
                    "Could not connect to server. Please try again.",
                    "error"
                );

            } finally {

                if (forgotLink) {
                    forgotLink.style.pointerEvents = "";
                    forgotLink.style.opacity = "";
                }
            }

        });
    }

    // ---------- REGISTER ----------
    var registerForm =
        document.getElementById("authorRegisterForm");

    var registerBtn =
        document.getElementById("registerBtn");

    var registerMessageEl =
        document.getElementById("register-message");

    if (registerForm) {

        registerForm.addEventListener(
            "submit",
            async function (e) {

                e.preventDefault();

                hideMessage(registerMessageEl);

                var username =
                    document.getElementById("regUsername")
                        .value.trim();

                var firstName =
                    document.getElementById("regFirstName")
                        .value.trim();

                var lastName =
                    document.getElementById("regLastName")
                        .value.trim();

                var email =
                    document.getElementById("regEmail")
                        .value.trim();

                var password =
                    document.getElementById("regPassword")
                        .value;

                var confirmPassword =
                    document.getElementById("regConfirmPassword")
                        .value;

                if (!username) {

                    showMessage(
                        registerMessageEl,
                        "Username is required",
                        "error"
                    );

                    return;
                }

                if (!firstName) {

                    showMessage(
                        registerMessageEl,
                        "First name is required",
                        "error"
                    );

                    return;
                }

                if (!lastName) {

                    showMessage(
                        registerMessageEl,
                        "Last name is required",
                        "error"
                    );

                    return;
                }

                if (!email) {

                    showMessage(
                        registerMessageEl,
                        "Email is required",
                        "error"
                    );

                    return;
                }

                if (!isValidEmail(email)) {

                    showMessage(
                        registerMessageEl,
                        "Please enter a valid email address",
                        "error"
                    );

                    return;
                }

                if (!password) {

                    showMessage(
                        registerMessageEl,
                        "Password is required",
                        "error"
                    );

                    return;
                }

                if (password.length < 8) {

                    showMessage(
                        registerMessageEl,
                        "Password must contain at least 8 characters",
                        "error"
                    );

                    return;
                }

                if (password !== confirmPassword) {

                    showMessage(
                        registerMessageEl,
                        "Password and Confirm Password do not match",
                        "error"
                    );

                    return;
                }

                registerBtn.disabled = true;
                registerBtn.textContent = "Registering...";

                try {

                    // Existing RegisterRequest – role forced to AUTHOR
                    var res = await fetch(
                        API_BASE + "/register",
                        {
                            method: "POST",
                            headers: {
                                "Content-Type": "application/json"
                            },
                            body: JSON.stringify({
                                username: username,
                                firstName: firstName,
                                lastName: lastName,
                                email: email,
                                password: password,
                                role: "AUTHOR"
                            })
                        }
                    );

                    var data = await res.json();

                    if (data.success) {

                        showMessage(
                            registerMessageEl,
                            "Registration successful. You can now log in with your email and password.",
                            "success"
                        );

                        registerForm.reset();

                        var loginEmail =
                            document.getElementById("loginEmail");

                        if (loginEmail) {
                            loginEmail.value = email;
                        }

                    } else {

                        showMessage(
                            registerMessageEl,
                            data.message ||
                            "Registration failed. Please try again.",
                            "error"
                        );
                    }

                } catch (err) {

                    showMessage(
                        registerMessageEl,
                        "Could not connect to server. Please try again.",
                        "error"
                    );

                } finally {

                    registerBtn.disabled = false;
                    registerBtn.textContent = "Register";
                }
            }
        );
    }

})();
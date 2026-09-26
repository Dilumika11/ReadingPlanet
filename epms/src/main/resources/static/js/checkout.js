(function () {
    "use strict";

    var root = document.getElementById("checkoutRoot");
    var state = {
        deliveryMethod: "SHIP",
        shippingOption: "KOOMBIYO",
        paymentMethod: "COD"
    };

    var DISTRICT_CITIES = {
        "Colombo": [
            "Colombo 1", "Colombo 2", "Colombo 3", "Colombo 4", "Colombo 5",
            "Colombo 6", "Colombo 7", "Colombo 8", "Colombo 9", "Colombo 10",
            "Colombo 11", "Colombo 12", "Colombo 13", "Colombo 14", "Colombo 15",
            "Dehiwala", "Mount Lavinia", "Moratuwa", "Kotte", "Maharagama",
            "Nugegoda", "Battaramulla", "Rajagiriya", "Homagama", "Piliyandala",
            "Kesbewa", "Boralesgamuwa"
        ],
        "Gampaha": [
            "Gampaha", "Negombo", "Kelaniya", "Kiribathgoda", "Ja-Ela",
            "Wattala", "Kandana", "Minuwangoda", "Divulapitiya", "Veyangoda",
            "Nittambuwa", "Ragama"
        ],
        "Kalutara": [
            "Kalutara", "Panadura", "Horana", "Beruwala", "Aluthgama",
            "Matugama", "Wadduwa", "Bandaragama"
        ],
        "Kandy": [
            "Kandy", "Peradeniya", "Katugastota", "Gampola", "Nawalapitiya",
            "Akurana", "Pilimatalawa", "Kadugannawa"
        ],
        "Galle": [
            "Galle", "Hikkaduwa", "Ambalangoda", "Elpitiya", "Baddegama", "Unawatuna"
        ],
        "Matara": [
            "Matara", "Weligama", "Dikwella", "Akuressa", "Kamburupitiya"
        ],
        "Kurunegala": [
            "Kurunegala", "Kuliyapitiya", "Pannala", "Narammala", "Polgahawela", "Mawathagama"
        ],
        "Other": ["Other"]
    };

    function token() {
        return localStorage.getItem("rp_customer_token") || sessionStorage.getItem("rp_customer_token");
    }

    function money(v) {
        return "Rs. " + Number(v || 0).toLocaleString(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }

    function escapeHtml(s) {
        return String(s == null ? "" : s)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }

    /** Delivery is included in the book price, so the order total is the cart subtotal. */
    function shippingFee() {
        return 0;
    }

    function fillCities(district, selectedCity) {
        var citySelect = document.getElementById("coCity");
        if (!citySelect) return;

        var cities = DISTRICT_CITIES[district] || [];
        citySelect.innerHTML = '<option value="">Select a city</option>';

        cities.forEach(function (city) {
            var opt = document.createElement("option");
            opt.value = city;
            opt.textContent = city;
            if (selectedCity && selectedCity === city) {
                opt.selected = true;
            }
            citySelect.appendChild(opt);
        });
    }

    function onlyDigits(s) {
        return String(s || "").replace(/\D/g, "");
    }

    function formatCardNumber(value) {
        var d = onlyDigits(value).slice(0, 16);
        return d.replace(/(\d{4})(?=\d)/g, "$1 ").trim();
    }

    function formatExpiry(value) {
        var d = onlyDigits(value).slice(0, 4);
        if (d.length >= 3) return d.slice(0, 2) + "/" + d.slice(2);
        return d;
    }

    function isValidCardNumber(num) {
        // DEMO MODE: accept any 13-19 digit card number (no Luhn)
        var s = onlyDigits(num);
        return s.length >= 13 && s.length <= 19;
    }

    function isValidExpiry(mmYy) {
        // DEMO: MM/YY with month 01-12
        var m = String(mmYy || "").match(/^(\d{2})\/(\d{2})$/);
        if (!m) return false;
        var month = parseInt(m[1], 10);
        return month >= 1 && month <= 12;
    }

    function isValidCvv(cvv) {
        return /^\d{3,4}$/.test(String(cvv || "").trim());
    }

    function detectCardBrand(number) {
        var s = onlyDigits(number);
        if (/^4/.test(s)) return "VISA";
        if (/^5[1-5]/.test(s) || /^2[2-7]/.test(s)) return "MASTER";
        if (/^3[47]/.test(s)) return "AMEX";
        return "CARD";
    }

    function resolvePaymentLabel() {
        if (state.paymentMethod === "PAYHERE") {
            var num = ((document.getElementById("cardNumber") || {}).value || "").trim();
            var brand = detectCardBrand(num);
            if (brand === "AMEX") return "AMEX";
            return "VISA/MASTER";
        }
        if (state.paymentMethod === "MINTPAY") return "MINTPAY";
        if (state.paymentMethod === "COD") return "COD";
        return state.paymentMethod;
    }


    function validatePayment() {
        if (state.paymentMethod === "COD" || state.paymentMethod === "MINTPAY") {
            return { ok: true };
        }

        var name = ((document.getElementById("cardName") || {}).value || "").trim();
        var number = ((document.getElementById("cardNumber") || {}).value || "").trim();
        var expiry = ((document.getElementById("cardExpiry") || {}).value || "").trim();
        var cvv = ((document.getElementById("cardCvv") || {}).value || "").trim();

        if (!name || name.length < 3) {
            return { ok: false, message: "Please enter the card holder name." };
        }
        if (!isValidCardNumber(number)) {
            return { ok: false, message: "Please enter a valid card number." };
        }
        if (!isValidExpiry(expiry)) {
            return { ok: false, message: "Please enter a valid expiry date (MM/YY)." };
        }
        if (!isValidCvv(cvv)) {
            return { ok: false, message: "Please enter a valid CVV (3 or 4 digits)." };
        }
        return { ok: true };
    }

    function requireLogin() {
        if (!token()) {
            root.innerHTML =
                '<p class="checkout-msg">Please <a href="/account.html">log in</a> to checkout.<br>' +
                "Your cart items are saved in this browser.</p>";
            return false;
        }
        return true;
    }

    function getFormSnapshot() {
        return {
            email: (document.getElementById("coEmail") || {}).value || "",
            first: (document.getElementById("coFirst") || {}).value || "",
            last: (document.getElementById("coLast") || {}).value || "",
            phone: (document.getElementById("coPhone") || {}).value || "",
            address: (document.getElementById("coAddress") || {}).value || "",
            postal: (document.getElementById("coPostal") || {}).value || "",
            district: (document.getElementById("coDistrict") || {}).value || "",
            city: (document.getElementById("coCity") || {}).value || "",
            country: (document.getElementById("coCountry") || {}).value || "Sri Lanka",
            note: (document.getElementById("coNote") || {}).value || "",
            noteOn: !!(document.getElementById("coNoteToggle") || {}).checked,
            terms: !!(document.getElementById("coTerms") || {}).checked
        };
    }

    function restoreFormSnapshot(snap) {
        if (!snap) return;
        var set = function (id, val) {
            var el = document.getElementById(id);
            if (el && val != null) el.value = val;
        };
        set("coEmail", snap.email);
        set("coFirst", snap.first);
        set("coLast", snap.last);
        set("coPhone", snap.phone);
        set("coAddress", snap.address);
        set("coPostal", snap.postal);
        set("coCountry", snap.country || "Sri Lanka");

        var districtEl = document.getElementById("coDistrict");
        if (districtEl && snap.district) {
            districtEl.value = snap.district;
            fillCities(snap.district, snap.city || "");
        }

        if (document.getElementById("coNoteToggle")) {
            document.getElementById("coNoteToggle").checked = !!snap.noteOn;
        }
        if (document.getElementById("coNote")) {
            document.getElementById("coNote").value = snap.note || "";
            document.getElementById("coNote").style.display = snap.noteOn ? "block" : "none";
        }
        if (document.getElementById("coTerms")) {
            document.getElementById("coTerms").checked = !!snap.terms;
        }

    }

    function render() {
        if (!requireLogin()) return;
        if (!window.RpCart) {
            root.innerHTML = '<p class="checkout-msg">Cart unavailable.</p>';
            return;
        }

        var cart = RpCart.read();
        if (!cart.length) {
            root.innerHTML =
                '<p class="checkout-msg">Your cart is empty. <a href="/store.html">Continue shopping</a></p>';
            return;
        }

        var sub = RpCart.subtotal(cart);
        var ship = shippingFee();
        var total = sub + ship;

        var summaryItems = cart.map(function (item) {
            var cover = item.coverImage
                ? '<img src="' + escapeHtml(item.coverImage) + '" alt="">'
                : '<div style="width:48px;height:64px;background:#edf2f7;border-radius:4px;"></div>';
            return (
                '<div class="summary-item">' + cover +
                '<div><div class="t">' + escapeHtml(item.title) + "</div>" +
                '<div class="q">Qty: ' + (parseInt(item.qty, 10) || 1) + "</div></div>" +
                "<div>" + money((Number(item.price) || 0) * (parseInt(item.qty, 10) || 1)) + "</div></div>"
            );
        }).join("");

        var districtOptions = Object.keys(DISTRICT_CITIES).map(function (d) {
            return '<option value="' + escapeHtml(d) + '">' + escapeHtml(d) + "</option>";
        }).join("");

        root.innerHTML =
            '<div class="checkout-layout">' +
            '<div class="checkout-main">' +
            '<p class="checkout-note">Please fill in English</p>' +
            '<div id="checkoutError" class="checkout-error" style="display:none;"></div>' +

            '<div class="checkout-section"><h2>Contact information</h2>' +
            '<div class="form-group"><label>Email address</label>' +
            '<input type="email" id="coEmail" required></div></div>' +

            '<div class="checkout-section"><h2>Delivery</h2>' +
            '<div class="delivery-tabs">' +
            '<button type="button" id="tabShip" class="' + (state.deliveryMethod === "SHIP" ? "active" : "") + '"><i class="fas fa-truck"></i> Ship</button>' +
            '<button type="button" id="tabPickup" class="' + (state.deliveryMethod === "PICKUP" ? "active" : "") + '"><i class="fas fa-store"></i> Store Pickup</button>' +
            "</div></div>" +

            '<div class="checkout-section" id="shipAddressBlock" style="' + (state.deliveryMethod === "PICKUP" ? "display:none;" : "") + '">' +
            "<h2>Shipping address</h2>" +
            '<div class="form-group"><label>Country/Region</label>' +
            '<select id="coCountry"><option>Sri Lanka</option></select></div>' +
            '<div class="form-row">' +
            '<div class="form-group"><label>First name</label><input type="text" id="coFirst" required></div>' +
            '<div class="form-group"><label>Last name</label><input type="text" id="coLast" required></div>' +
            "</div>" +
            '<div class="form-group"><label>Address</label><input type="text" id="coAddress" required placeholder="Apartment, suite, etc."></div>' +
            '<div class="form-row">' +
            '<div class="form-group"><label>Postal code</label><input type="text" id="coPostal"></div>' +
            '<div class="form-group"><label>District</label>' +
            '<select id="coDistrict">' +
            '<option value="">Select a district</option>' +
            districtOptions +
            "</select></div></div>" +
            '<div class="form-row">' +
            '<div class="form-group"><label>Phone</label><input type="tel" id="coPhone"></div>' +
            '<div class="form-group"><label>City</label>' +
            '<select id="coCity"><option value="">Select a city</option></select>' +
            "</div></div></div>" +

            '<div class="checkout-section" id="shipOptionsBlock" style="' + (state.deliveryMethod === "PICKUP" ? "display:none;" : "") + '">' +
            "<h2>Shipping options</h2>" +
            '<div class="option-list">' +
            '<label class="option-row"><input type="radio" name="shipOpt" value="KOOMBIYO"' +
            (state.shippingOption === "KOOMBIYO" ? " checked" : "") +
            '> Koombiyo Courier <span class="price">Free</span></label>' +
            '<label class="option-row"><input type="radio" name="shipOpt" value="SL_POST"' +
            (state.shippingOption === "SL_POST" ? " checked" : "") +
            '> SL Post <span class="price">Free</span></label>' +
            "</div></div>" +

            '<div class="checkout-section"><h2>Payment options</h2>' +
            '<div class="option-list">' +

            '<label class="option-row payment-option' + (state.paymentMethod === "COD" ? " selected" : "") + '">' +
            '<input type="radio" name="payOpt" value="COD"' +
            (state.paymentMethod === "COD" ? " checked" : "") +
            '> <span class="pay-title">Cash on Delivery</span>' +
            "</label>" +
            '<p class="pay-help">Online card payment is not connected yet. You pay the courier when the books arrive.</p>' +

            "</div></div>" +

            '<div class="checkout-section">' +
            '<label class="checkbox-row"><input type="checkbox" id="coNoteToggle"> Add a note to your order</label>' +
            '<textarea id="coNote" rows="2" style="display:none;width:100%;margin-top:0.5rem;"></textarea>' +
            '<label class="checkbox-row"><input type="checkbox" id="coTerms"> By proceeding you agree to our Terms and Privacy Policy.</label>' +
            '<button type="button" class="btn-place-order" id="placeOrderBtn">Place Order</button>' +
            "</div></div>" +

            '<aside class="order-summary"><h2>Order summary</h2>' +
            summaryItems +
            '<div class="summary-row"><span>Subtotal</span><span>' + money(sub) + "</span></div>" +
            '<div class="summary-row"><span>Delivery</span><span>' + money(ship) + "</span></div>" +
            '<div class="summary-row total"><span>Total</span><span>' + money(total) + "</span></div>" +
            "</aside></div>";

        bind();
        prefillProfile();
    }

    function bind() {
        var tabShip = document.getElementById("tabShip");
        var tabPickup = document.getElementById("tabPickup");

        if (tabShip) {
            tabShip.onclick = function () {
                var snap = getFormSnapshot();
                state.deliveryMethod = "SHIP";
                if (!state.shippingOption || state.shippingOption === "NONE") {
                    state.shippingOption = "KOOMBIYO";
                }
                render();
                restoreFormSnapshot(snap);
            };
        }

        if (tabPickup) {
            tabPickup.onclick = function () {
                var snap = getFormSnapshot();
                state.deliveryMethod = "PICKUP";
                state.shippingOption = "NONE";
                render();
                restoreFormSnapshot(snap);
            };
        }

        document.querySelectorAll('input[name="shipOpt"]').forEach(function (r) {
            r.onchange = function () {
                var snap = getFormSnapshot();
                state.shippingOption = this.value;
                render();
                restoreFormSnapshot(snap);
            };
        });

        document.querySelectorAll('input[name="payOpt"]').forEach(function (r) {
            r.onchange = function () {
                var snap = getFormSnapshot();
                state.paymentMethod = this.value;
                render();
                restoreFormSnapshot(snap);
            };
        });

        var cardNumber = document.getElementById("cardNumber");
        if (cardNumber) {
            cardNumber.addEventListener("input", function () {
                this.value = formatCardNumber(this.value);
            });
        }
        var cardExpiry = document.getElementById("cardExpiry");
        if (cardExpiry) {
            cardExpiry.addEventListener("input", function () {
                this.value = formatExpiry(this.value);
            });
        }

        var districtEl = document.getElementById("coDistrict");
        if (districtEl) {
            districtEl.onchange = function () {
                fillCities(this.value, "");
            };
            if (districtEl.value) {
                fillCities(districtEl.value, "");
            }
        }

        var noteToggle = document.getElementById("coNoteToggle");
        if (noteToggle) {
            noteToggle.onchange = function () {
                var note = document.getElementById("coNote");
                if (note) note.style.display = this.checked ? "block" : "none";
            };
        }

        var wholesaleToggle = document.getElementById("coWholesale");
        var wholesaleNote = document.getElementById("coWholesaleNote");
        if (wholesaleToggle && wholesaleNote) {
            wholesaleToggle.onchange = function () {
                wholesaleNote.style.display = this.checked ? "block" : "none";
            };
        }

        var placeBtn = document.getElementById("placeOrderBtn");
        if (placeBtn) placeBtn.onclick = placeOrder;
    }

    function prefillProfile() {
        var t = token();
        if (!t) return;

        fetch("/api/customer/profile", {
            headers: { Authorization: "Bearer " + t }
        })
            .then(function (r) { return r.json(); })
            .then(function (data) {
                if (!data || !data.success || !data.data) return;
                var p = data.data;

                var emailEl = document.getElementById("coEmail");
                var firstEl = document.getElementById("coFirst");
                var lastEl = document.getElementById("coLast");
                var phoneEl = document.getElementById("coPhone");

                if (emailEl && p.email && !emailEl.value) emailEl.value = p.email;
                if (firstEl && p.firstName && !firstEl.value) firstEl.value = p.firstName;
                if (lastEl && p.lastName && !lastEl.value) lastEl.value = p.lastName;
                if (phoneEl && p.phoneNumber && !phoneEl.value) phoneEl.value = p.phoneNumber;
            })
            .catch(function () {});
    }

    function showError(msg) {
        var el = document.getElementById("checkoutError");
        if (!el) return;
        el.style.display = "block";
        el.textContent = msg;
        el.scrollIntoView({ behavior: "smooth", block: "center" });
    }

    function placeOrder() {
        if (!token()) {
            window.location.href = "/account.html";
            return;
        }

        var terms = document.getElementById("coTerms");
        if (!terms || !terms.checked) {
            showError("Please agree to the Terms and Conditions.");
            return;
        }

        var payCheck = validatePayment();
        if (!payCheck.ok) {
            showError(payCheck.message);
            return;
        }

        var email = (document.getElementById("coEmail") || {}).value || "";
        var firstName = (document.getElementById("coFirst") || {}).value || "";
        var lastName = (document.getElementById("coLast") || {}).value || "";
        var phone = (document.getElementById("coPhone") || {}).value || "";
        var addressLine = (document.getElementById("coAddress") || {}).value || "";
        var city = (document.getElementById("coCity") || {}).value || "";
        var district = (document.getElementById("coDistrict") || {}).value || "";
        var postalCode = (document.getElementById("coPostal") || {}).value || "";
        var country = (document.getElementById("coCountry") || {}).value || "Sri Lanka";
        var noteToggle = document.getElementById("coNoteToggle");
        var noteEl = document.getElementById("coNote");

        var note = noteToggle && noteToggle.checked && noteEl ? noteEl.value.trim() : "";
        var recipient = (firstName.trim() + " " + lastName.trim()).trim();
        var shipVia = state.shippingOption === "SL_POST" ? "SL Post" : "Koombiyo Courier";
        var address = state.deliveryMethod === "PICKUP"
            ? "Store pickup, Reading Planet"
            : [addressLine.trim(), city, district, postalCode.trim(), country].filter(Boolean).join(", ") + " (" + shipVia + ")";
        if (note) address += ". Note: " + note;
        var body = {
            recipientName: recipient,
            recipientPhone: phone.trim(),
            shippingAddress: address.slice(0, 255)
        };

        if (!email.trim() || !firstName.trim()) {
            showError("Email and first name are required.");
            return;
        }
        if (!body.recipientPhone) {
            showError("A phone number is required so the courier can reach you.");
            return;
        }

        if (state.deliveryMethod === "SHIP") {
            if (!addressLine.trim()) {
                showError("Shipping address is required.");
                return;
            }
            if (!district) {
                showError("Please select a district.");
                return;
            }
            if (!city) {
                showError("Please select a city.");
                return;
            }
        }

        var btn = document.getElementById("placeOrderBtn");
        btn.disabled = true;
        btn.textContent = "Placing order...";

        var syncPromise = RpCart.syncToServer ? RpCart.syncToServer() : Promise.resolve();

        syncPromise
            .then(function () {
                return fetch("/api/customer/orders", {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                        Authorization: "Bearer " + token()
                    },
                    body: JSON.stringify(body)
                });
            })
            .then(function (r) { return r.json(); })
            .then(function (data) {
                if (!data.success) {
                    showError(data.message || "Checkout failed");
                    btn.disabled = false;
                    btn.textContent = "Place Order";
                    return;
                }

                RpCart.clear();
                var order = data.data.order || data.data;
                root.innerHTML =
                    '<div class="checkout-success">' +
                    "<h2>Order placed successfully</h2>" +
                    "<p>Order number: <strong>" + escapeHtml(order.orderNumber) + "</strong></p>" +
                    "<p>Total: <strong>" + money(order.totalAmount) + "</strong></p>" +
                    "<p>Payment: <strong>" + "Cash on Delivery" + "</strong></p>" +
                    "<p>Status: " + escapeHtml(order.orderStatus) + "</p>" +
                    '<p style="margin-top:1.5rem;"><a href="/store.html">Continue shopping</a> · ' +
                    '<a href="/account.html">Track this order</a></p></div>';
            })
            .catch(function () {
                showError("Network error. Please try again.");
                btn.disabled = false;
                btn.textContent = "Place Order";
            });
    }

    render();
})();
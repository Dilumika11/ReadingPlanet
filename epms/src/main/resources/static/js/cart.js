
(function () {
    "use strict";
    var root = document.getElementById("cartRoot");
    var couponCode = "";

    function escapeHtml(s) {
        return String(s == null ? "" : s)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;")
            .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }
    function money(v) {
        return "Rs. " + Number(v || 0).toLocaleString(undefined, {
            minimumFractionDigits: 2, maximumFractionDigits: 2
        });
    }
    function render() {
        if (!window.RpCart) {
            root.innerHTML = '<p class="cart-empty-msg">Cart is unavailable.</p>';
            return;
        }
        var cart = RpCart.read();
        if (!cart.length) {
            root.innerHTML = '<p class="cart-empty-msg">Your cart is currently empty.<br><a href="/store.html">Return to Online Store</a></p>';
            RpCart.refreshBadge();
            return;
        }
        var rows = cart.map(function (item) {
            var qty = parseInt(item.qty, 10) || 1;
            var line = (Number(item.price) || 0) * qty;
            var cover = item.coverImage
                ? '<img src="' + escapeHtml(item.coverImage) + '" alt="">'
                : '<i class="fas fa-book"></i>';
            return (
                '<tr data-id="' + item.bookId + '">' +
                '<td data-label="Product"><div class="cart-product">' +
                '<button type="button" class="cart-remove-x" title="Remove">×</button>' +
                '<div class="cart-thumb">' + cover + '</div>' +
                '<p class="cart-product-title"><a href="/book.html?id=' + item.bookId + '">' +
                escapeHtml(item.title) + '</a></p></div></td>' +
                '<td class="cart-unit-price">' + (item.discountPercent && item.originalPrice ? ('<span style="text-decoration:line-through;color:#a0aec0;margin-right:0.35rem;">' + money(item.originalPrice) + '</span>') : '') + money(item.price) + '</td>' +
                '<td><div class="qty-stepper">' +
                '<button type="button" class="qty-minus">−</button>' +
                '<input type="number" class="qty-input" min="1" value="' + qty + '">' +
                '<button type="button" class="qty-plus">+</button>' +
                '</div></td>' +
                '<td class="cart-line-total">' + money(line) + '</td></tr>'
            );
        }).join("");
        var subtotal = RpCart.subtotal(cart);
        root.innerHTML =
            '<div class="cart-layout"><div class="cart-main">' +
            '<table class="cart-table"><thead><tr>' +
            '<th>Product</th><th>Price</th><th>Quantity</th><th>Subtotal</th>' +
            '</tr></thead><tbody>' + rows + '</tbody></table>' +
            '<div class="cart-actions-row">' +
            '<input type="text" class="cart-coupon-input" id="couponInput" placeholder="Coupon code" value="' + escapeHtml(couponCode) + '">' +
            '<button type="button" class="btn-cart btn-apply-coupon" id="applyCouponBtn">Apply coupon</button>' +
            '<button type="button" class="btn-cart btn-update-cart" id="updateCartBtn">Update cart</button>' +
            '</div><p class="cart-msg" id="cartMsg"></p></div>' +
            '<aside class="cart-totals"><h2>Cart totals</h2>' +
            '<div class="cart-totals-row"><span>Subtotal</span><span>' + money(subtotal) + '</span></div>' +
            '<div class="cart-totals-row total"><span>Total</span><span>' + money(subtotal) + '</span></div>' +
            '<a class="btn-checkout" href="#" id="checkoutBtn">Proceed to checkout</a>' +
            '</aside></div>';

        root.querySelectorAll("tr[data-id]").forEach(function (tr) {
            var id = tr.getAttribute("data-id");
            var input = tr.querySelector(".qty-input");
            tr.querySelector(".cart-remove-x").onclick = function () { RpCart.remove(id); render(); };
            tr.querySelector(".qty-minus").onclick = function () {
                RpCart.setQuantity(id, (parseInt(input.value, 10) || 1) - 1); render();
            };
            tr.querySelector(".qty-plus").onclick = function () {
                RpCart.setQuantity(id, (parseInt(input.value, 10) || 1) + 1); render();
            };
            input.onchange = function () { RpCart.setQuantity(id, input.value); render(); };
        });
        document.getElementById("updateCartBtn").onclick = function () {
            root.querySelectorAll("tr[data-id]").forEach(function (tr) {
                RpCart.setQuantity(tr.getAttribute("data-id"), tr.querySelector(".qty-input").value);
            });
            render();
            document.getElementById("cartMsg").textContent = "Cart updated.";
        };
        document.getElementById("applyCouponBtn").onclick = function () {
            couponCode = (document.getElementById("couponInput").value || "").trim();
            document.getElementById("cartMsg").textContent = couponCode
                ? ('Coupon "' + couponCode + '" noted. Discount rules can be connected later.')
                : "Enter a coupon code.";
        };
        document.getElementById("checkoutBtn").onclick = function (e) {
            e.preventDefault();
            window.location.href = "/checkout.html";
        };
    }
    // The server may trim a quantity to the stock on hand; redraw when it answers.
    window.addEventListener("rp-cart-updated", function () { render(); });
    window.addEventListener("rp-cart-error", function (e) {
        var msg = document.getElementById("cartMsg");
        if (msg) msg.textContent = e.detail.message;
    });
    render();
})();

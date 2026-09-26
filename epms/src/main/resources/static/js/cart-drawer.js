/**
 * Reading Planet – slide-out cart drawer (Muses-style)
 * Depends on window.RpCart from cart-utils.js
 */
(function (global) {
    "use strict";

    function ensureDom() {
        if (document.getElementById("rpCartDrawer")) return;

        var overlay = document.createElement("div");
        overlay.className = "rp-cart-overlay";
        overlay.id = "rpCartOverlay";

        var drawer = document.createElement("aside");
        drawer.className = "rp-cart-drawer";
        drawer.id = "rpCartDrawer";
        drawer.setAttribute("aria-hidden", "true");
        drawer.innerHTML =
            '<div class="rp-cart-drawer-header">' +
            "<h2>Shopping cart</h2>" +
            '<button type="button" class="rp-cart-close" id="rpCartClose">CLOSE ×</button>' +
            "</div>" +
            '<div class="rp-cart-drawer-body" id="rpCartDrawerBody"></div>' +
            '<div class="rp-cart-drawer-footer" id="rpCartDrawerFooter"></div>';

        document.body.appendChild(overlay);
        document.body.appendChild(drawer);

        overlay.addEventListener("click", close);
        document.getElementById("rpCartClose").addEventListener("click", close);
        document.addEventListener("keydown", function (e) {
            if (e.key === "Escape") close();
        });
    }

    function money(v) {
        return "Rs. " + Number(v || 0).toLocaleString(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }

    function escapeHtml(s) {
        return String(s == null ? "" : s)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;")
            .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }

    function render() {
        ensureDom();
        if (!global.RpCart) return;

        var cart = RpCart.read();
        var body = document.getElementById("rpCartDrawerBody");
        var footer = document.getElementById("rpCartDrawerFooter");

        if (!cart.length) {
            body.innerHTML = '<div class="rp-cart-empty">Your cart is empty.</div>';
            footer.innerHTML =
                '<div class="rp-cart-drawer-actions">' +
                '<a class="rp-cart-btn rp-cart-btn-view" href="/store.html">CONTINUE SHOPPING</a>' +
                "</div>";
            return;
        }

        body.innerHTML = cart.map(function (item) {
            var cover = item.coverImage
                ? '<img src="' + escapeHtml(item.coverImage) + '" alt="">'
                : '<i class="fas fa-book"></i>';
            var line = (Number(item.price) || 0) * (parseInt(item.qty, 10) || 0);
            return (
                '<div class="rp-cart-item" data-id="' + item.bookId + '">' +
                '<div class="rp-cart-item-cover">' + cover + "</div>" +
                '<div class="rp-cart-item-info">' +
                "<h3>" + escapeHtml(item.title) + "</h3>" +
                "<p>" + (parseInt(item.qty, 10) || 1) + " × " + money(item.price) + "</p>" +
                "</div>" +
                '<button type="button" class="rp-cart-item-remove" aria-label="Remove">×</button>' +
                "</div>"
            );
        }).join("");

        body.querySelectorAll(".rp-cart-item-remove").forEach(function (btn) {
            btn.addEventListener("click", function () {
                var id = btn.closest(".rp-cart-item").getAttribute("data-id");
                RpCart.remove(id);
                render();
            });
        });

        footer.innerHTML =
            '<div class="rp-cart-subtotal"><span>Subtotal:</span><strong>' +
            money(RpCart.subtotal(cart)) +
            "</strong></div>" +
            '<div class="rp-cart-drawer-actions">' +
            '<a class="rp-cart-btn rp-cart-btn-view" href="/cart.html">VIEW CART</a>' +
            '<a class="rp-cart-btn rp-cart-btn-checkout" href="/cart.html">CHECKOUT</a>' +
            "</div>";
    }

    function open() {
        ensureDom();
        render();
        document.getElementById("rpCartOverlay").classList.add("open");
        var drawer = document.getElementById("rpCartDrawer");
        drawer.classList.add("open");
        drawer.setAttribute("aria-hidden", "false");
        document.body.style.overflow = "hidden";
    }

    function close() {
        var overlay = document.getElementById("rpCartOverlay");
        var drawer = document.getElementById("rpCartDrawer");
        if (!drawer) return;
        overlay.classList.remove("open");
        drawer.classList.remove("open");
        drawer.setAttribute("aria-hidden", "true");
        document.body.style.overflow = "";
    }

    global.RpCartDrawer = { open: open, close: close, render: render };

    global.addEventListener("rp-cart-updated", function () {
        if (document.getElementById("rpCartDrawer") &&
            document.getElementById("rpCartDrawer").classList.contains("open")) {
            render();
        }
    });
})(window);

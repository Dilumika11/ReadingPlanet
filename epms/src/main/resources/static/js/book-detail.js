(function () {
    "use strict";

    const root = document.getElementById("detailRoot");
    const id = new URLSearchParams(window.location.search).get("id");

    function escapeHtml(s) {
        return String(s == null ? "" : s)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }

    function priceDetailHtml(b) {
        var disc = Number(b.discountPercent) || 0;
        var effective = b.price;
        var original = b.originalPrice != null ? b.originalPrice : null;
        if (disc > 0 && original != null) {
            return '<div class="detail-price"><span class="detail-price-old">' + money(original) + '</span>' +
                money(effective) + '<span class="detail-discount-badge">-' + Math.round(disc) + '%</span></div>';
        }
        return '<div class="detail-price">' + money(effective) + '</div>';
    }
    function money(v) {
        if (v == null || v === "") return "—";
        return "Rs. " + Number(v).toLocaleString(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }

    if (!id) {
        root.innerHTML = '<p class="store-empty">Book not found.</p>';
        return;
    }

    fetch("/api/public/books/" + encodeURIComponent(id))
        .then(function (r) {
            if (!r.ok) throw new Error("not found");
            return r.json();
        })
        .then(function (res) {
            if (!res || !res.data) throw new Error("not found");
            const b = RpBooks.fromCatalog(res.data);

            document.getElementById("pageHeading").textContent = b.title || "Book details";
            document.getElementById("crumbTitle").textContent = b.title || "Book";
            document.title = (b.title || "Book") + " – Reading Planet";

            const cover = b.coverImage
                ? '<img src="' + escapeHtml(b.coverImage) + '" alt="' + escapeHtml(b.title) + '">'
                : '<div class="detail-cover-fallback"><i class="fas fa-book"></i></div>';

            root.innerHTML =
                '<div class="book-detail-card">' +
                '<div class="detail-cover">' + cover + "</div>" +
                '<div class="detail-info">' +
                "<h1>" + escapeHtml(b.title || "") + "</h1>" +
                '<p class="row"><strong>Author:</strong> ' + escapeHtml(b.authorName || "Unknown") + "</p>" +
                '<p class="row"><strong>Category:</strong> ' + escapeHtml(b.categoryName || "—") + "</p>" +
                '<p class="row"><strong>Genre:</strong> ' + escapeHtml(b.genreName || "—") + "</p>" +
                '<p class="row"><strong>ISBN:</strong> ' + escapeHtml(b.isbn || "—") + "</p>" +
                '<p class="row"><strong>Available stock:</strong> ' +
                (b.orderable ? b.availableStock : "Out of stock") + "</p>" +
                priceDetailHtml(b) +
                '<div class="detail-synopsis">' +
                escapeHtml(b.description || "No description available for this book.") +
                "</div>" +
                '<div class="purchase-row">' +
                '<span class="qty-label">Quantity</span>' +
                '<div class="qty-stepper">' +
                '<button type="button" id="qtyMinus" aria-label="Decrease">−</button>' +
                '<input type="number" id="detailQty" min="1" value="1">' +
                '<button type="button" id="qtyPlus" aria-label="Increase">+</button>' +
                '</div>' +
                '<button type="button" class="btn-add-cart-red" id="addCartDetail">' +
                '<i class="fas fa-shopping-bag"></i> Add to cart</button>' +
                '<button type="button" class="btn-wishlist-text" id="addWishlistDetail">' +
                '<i class="far fa-heart"></i> Add to wishlist</button>' +
                '</div>' +
                '<p style="margin-top:1.25rem;"><a class="btn-book btn-book-outline" href="/store.html">Back to Store</a></p>' +
                "</div></div>";

            document.getElementById("addCartDetail").addEventListener("click", function () {
                var q = parseInt((document.getElementById("detailQty") || {}).value, 10) || 1;
                const btn = document.getElementById("addCartDetail");
                if (!b.orderable) {
                    btn.textContent = "Out of stock";
                    return;
                }
                RpCart.add(b, q);
                btn.innerHTML = '<i class="fas fa-check"></i> Added';
                if (window.RpCartDrawer) RpCartDrawer.open();
                setTimeout(function () { btn.innerHTML = '<i class="fas fa-shopping-bag"></i> Add to cart'; }, 1200);
            });

            var qtyInput = document.getElementById("detailQty");
            document.getElementById("qtyMinus").onclick = function () {
                var v = parseInt(qtyInput.value, 10) || 1;
                qtyInput.value = Math.max(1, v - 1);
            };
            document.getElementById("qtyPlus").onclick = function () {
                var v = parseInt(qtyInput.value, 10) || 1;
                qtyInput.value = v + 1;
            };
            var wl = document.getElementById("addWishlistDetail");
            if (wl) wl.onclick = function () {
                var list = [];
                try { list = JSON.parse(localStorage.getItem("rp_wishlist") || "[]"); if (!Array.isArray(list)) list = []; } catch (e) { list = []; }
                if (list.indexOf(b.bookId) === -1) list.push(b.bookId);
                localStorage.setItem("rp_wishlist", JSON.stringify(list));
                wl.innerHTML = '<i class="fas fa-heart"></i> Added to wishlist';
                var badge = document.getElementById("wishlistBadge");
                if (badge) badge.textContent = String(list.length);
            };

        })
        .catch(function () {
            root.innerHTML = '<p class="store-empty">This book is not available.</p>';
        });
})();

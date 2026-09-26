/**
 * Reading Planet cart – localStorage + server sync when logged in
 * local key: rp_cart
 * API: /api/customer/cart (requires rp_customer_token)
 */
(function (global) {
    "use strict";

    var STORAGE_KEY = "rp_cart";

    function getToken() {
        return localStorage.getItem("rp_customer_token") || sessionStorage.getItem("rp_customer_token");
    }

    function authHeaders() {
        var t = getToken();
        var h = { "Content-Type": "application/json" };
        if (t) h["Authorization"] = "Bearer " + t;
        return h;
    }

    function readCart() {
        try {
            var cart = JSON.parse(localStorage.getItem(STORAGE_KEY) || "[]");
            return Array.isArray(cart) ? cart : [];
        } catch (e) {
            return [];
        }
    }

    function writeCart(cart) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(cart));
        global.dispatchEvent(new CustomEvent("rp-cart-updated", { detail: { cart: cart } }));
        updateBadges(cart);
    }

    function totalItems(cart) {
        return (cart || readCart()).reduce(function (sum, item) {
            return sum + (parseInt(item.qty, 10) || 0);
        }, 0);
    }

    function updateBadges(cart) {
        var count = totalItems(cart);
        var badge = document.getElementById("cartBadge");
        if (badge) badge.textContent = String(count);
    }

    function subtotal(cart) {
        return (cart || readCart()).reduce(function (sum, item) {
            return sum + (Number(item.price) || 0) * (parseInt(item.qty, 10) || 0);
        }, 0);
    }

    function addItem(book, qty) {
        if (!book || book.bookId == null) return readCart();
        if (book.orderable === false) {
            notify("\"" + (book.title || "This book") + "\" is out of stock right now");
            return readCart();
        }
        qty = parseInt(qty, 10);
        if (!qty || qty < 1) qty = 1;

        var cart = readCart();
        var existing = cart.find(function (x) { return Number(x.bookId) === Number(book.bookId); });
        if (existing) {
            existing.qty = (parseInt(existing.qty, 10) || 0) + qty;
            if (book.title) existing.title = book.title;
            if (book.authorName != null) existing.authorName = book.authorName;
            if (book.price != null) existing.price = book.price;
            if (book.originalPrice != null) existing.originalPrice = book.originalPrice;
            if (book.discountPercent != null) existing.discountPercent = book.discountPercent;
            if (book.coverImage != null) existing.coverImage = book.coverImage;
            if (book.availableStock != null) existing.availableStock = book.availableStock;
        } else {
            cart.push({
                bookId: book.bookId,
                title: book.title || ("Book #" + book.bookId),
                authorName: book.authorName || "",
                price: book.price != null ? book.price : 0,
                originalPrice: book.originalPrice != null ? book.originalPrice : book.price,
                discountPercent: Number(book.discountPercent) || 0,
                coverImage: book.coverImage || null,
                availableStock: book.availableStock != null ? book.availableStock : null,
                qty: qty
            });
        }
        writeCart(cart);

        // Keep the account cart in step when a customer is logged in
        if (getToken()) {
            api("POST", "/api/customer/cart", { catalogBookId: book.bookId, quantity: qty });
        }
        return cart;
    }

    /** Calls the account cart API, then reloads the server cart so stock limits win. */
    function api(method, url, body) {
        return fetch(url, {
            method: method,
            headers: authHeaders(),
            body: body ? JSON.stringify(body) : undefined
        }).then(function (r) {
            return r.json().catch(function () { return {}; }).then(function (data) {
                if (!r.ok || (data && data.success === false)) {
                    notify((data && data.message) || "Your cart could not be updated");
                }
                return syncFromServer();
            });
        }).catch(function () { return readCart(); });
    }

    function notify(message) {
        global.dispatchEvent(new CustomEvent("rp-cart-error", { detail: { message: message } }));
        if (global.console) console.warn(message);
    }

    function serverId(bookId) {
        var item = readCart().find(function (x) { return Number(x.bookId) === Number(bookId); });
        return item ? item.cartItemId : null;
    }

    function setQuantity(bookId, qty) {
        qty = parseInt(qty, 10);
        var itemId = serverId(bookId);
        var cart = readCart();
        var item = cart.find(function (x) { return Number(x.bookId) === Number(bookId); });
        if (!item) return cart;
        if (!qty || qty < 1) {
            cart = cart.filter(function (x) { return Number(x.bookId) !== Number(bookId); });
        } else {
            item.qty = qty;
        }
        writeCart(cart);
        if (getToken() && itemId) {
            if (!qty || qty < 1) api("DELETE", "/api/customer/cart/" + itemId);
            else api("PUT", "/api/customer/cart/" + itemId, { quantity: qty });
        }
        return cart;
    }

    function removeItem(bookId) {
        var itemId = serverId(bookId);
        var cart = readCart().filter(function (x) { return Number(x.bookId) !== Number(bookId); });
        writeCart(cart);
        if (getToken() && itemId) api("DELETE", "/api/customer/cart/" + itemId);
        return cart;
    }

    /** Empties the browser copy. The server empties the account cart itself at checkout. */
    function clearCart() {
        writeCart([]);
    }

    /** Load the account cart into localStorage (call after login) */
    function syncFromServer() {
        if (!getToken()) return Promise.resolve(readCart());
        return fetch("/api/customer/cart", { headers: authHeaders() })
            .then(function (r) { return r.json(); })
            .then(function (data) {
                if (!data || !data.success || !data.data) return readCart();
                var items = (data.data.items || []).map(fromServer);
                writeCart(items);
                return items;
            })
            .catch(function () { return readCart(); });
    }

    function fromServer(it) {
        return {
            bookId: it.catalogBookId,
            cartItemId: it.cartItemId,
            title: it.title,
            authorName: it.author || "",
            price: it.price,
            originalPrice: it.price,
            discountPercent: 0,
            coverImage: it.coverUrl || null,
            availableStock: it.available,
            enoughStock: it.enoughStock,
            qty: it.quantity
        };
    }

    /** Push the guest cart to the account after login, then load the merged cart */
    function syncToServer() {
        if (!getToken()) return Promise.resolve();
        var items = readCart().filter(function (x) { return !x.cartItemId; }).map(function (x) {
            return { catalogBookId: x.bookId, quantity: parseInt(x.qty, 10) || 1 };
        });
        if (!items.length) return syncFromServer();
        return fetch("/api/customer/cart/merge", {
            method: "POST",
            headers: authHeaders(),
            body: JSON.stringify(items)
        }).then(function () { return syncFromServer(); }).catch(function () {});
    }

    global.RPCart = global.RpCart = {
        read: readCart,
        write: writeCart,
        add: addItem,
        setQuantity: setQuantity,
        remove: removeItem,
        clear: clearCart,
        totalItems: totalItems,
        subtotal: subtotal,
        refreshBadge: function () { updateBadges(readCart()); },
        syncFromServer: syncFromServer,
        syncToServer: syncToServer,
        isLoggedIn: function () { return !!getToken(); }
    };

    /** Maps a book from /api/public/catalog to the shape the store pages use. */
    global.RpBooks = {
        fromCatalog: function (b) {
            return {
                bookId: b.bookId,
                title: b.title,
                authorName: b.author,
                authorId: b.authorId,
                categoryId: b.categoryId,
                categoryName: b.categoryName,
                genreId: b.genreId,
                genreName: b.genreName,
                isbn: b.isbn,
                price: b.price,
                originalPrice: b.price,
                discountPercent: 0,
                coverImage: b.coverUrl || null,
                description: b.blurb || "",
                availableStock: b.available,
                orderable: b.orderable,
                newArrival: b.newArrival
            };
        }
    };

    global.addEventListener("storage", function (e) {
        if (e.key === STORAGE_KEY) updateBadges(readCart());
    });

    // On load, if logged in, prefer server cart
    if (getToken()) {
        syncFromServer();
    } else {
        updateBadges(readCart());
    }
})(window);

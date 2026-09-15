/*
 * Online Store — renders the public catalogue (/api/public/catalog) grouped by
 * the admin-managed categories. Cart and wishlist are a local demo only
 * (localStorage) until Epic 3's order flow exists.
 */
(function () {
    "use strict";

    const chipRow = document.getElementById("chipRow");
    const sectionsEl = document.getElementById("sections");
    const modalBackdrop = document.getElementById("modalBackdrop");
    const modal = document.getElementById("modal");
    const toastEl = document.getElementById("toast");

    let catalog = { categories: [], books: [] };
    let activeCategoryId = null;   // null = All
    let query = "";

    // Icons for the category chips — matched by name, generic fallback otherwise.
    const CHIP_ICONS = {
        "fiction": "fa-book",
        "non-fiction": "fa-book-open",
        "mystery": "fa-user-secret",
        "short story": "fa-feather",
        "poetry": "fa-pen-nib",
        "children": "fa-child",
        "technology": "fa-laptop-code",
        "history": "fa-landmark",
        "romance": "fa-heart",
        "science": "fa-flask"
    };

    // Cover fallback palettes, cycled by book id
    const PALETTES = [
        ["#1568ae", "#0b2f52"], ["#b35c2b", "#4a2210"], ["#2f7d5a", "#0f3324"],
        ["#7a3e8f", "#2c1436"], ["#c7912a", "#5a3d05"], ["#3b4a6b", "#141a2b"]
    ];

    function escapeHtml(str) {
        if (str == null) return "";
        return String(str).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }

    function money(v) {
        return "Rs. " + Number(v).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    function chipIcon(name) {
        return CHIP_ICONS[(name || "").toLowerCase()] || "fa-tag";
    }

    // ---------- local demo cart / wishlist ----------
    function readList(key) {
        try { return JSON.parse(localStorage.getItem(key) || "[]"); } catch (e) { return []; }
    }
    function writeList(key, list) {
        try { localStorage.setItem(key, JSON.stringify(list)); } catch (e) { /* ignore */ }
    }
    function updateBadges() {
        const cart = readList("rp_cart");
        const wish = readList("rp_wishlist");
        const cartCount = cart.reduce(function (s, i) { return s + (i.qty || 1); }, 0);
        const cartEl = document.getElementById("cartCount");
        const wishEl = document.getElementById("wishlistCount");
        cartEl.textContent = cartCount;
        cartEl.classList.toggle("show", cartCount > 0);
        wishEl.textContent = wish.length;
        wishEl.classList.toggle("show", wish.length > 0);
    }
    function addToCart(book) {
        const cart = readList("rp_cart");
        const existing = cart.find(function (i) { return i.bookId === book.bookId; });
        if (existing) existing.qty = (existing.qty || 1) + 1;
        else cart.push({ bookId: book.bookId, title: book.title, price: book.price, qty: 1 });
        writeList("rp_cart", cart);
        updateBadges();
        toast('"' + book.title + '" added to cart');
    }
    function toggleWishlist(book) {
        let wish = readList("rp_wishlist");
        const idx = wish.findIndex(function (i) { return i.bookId === book.bookId; });
        if (idx >= 0) { wish.splice(idx, 1); toast('Removed "' + book.title + '" from wishlist'); }
        else { wish.push({ bookId: book.bookId, title: book.title }); toast('"' + book.title + '" saved to wishlist'); }
        writeList("rp_wishlist", wish);
        updateBadges();
    }

    let toastTimer = null;
    function toast(msg) {
        toastEl.textContent = msg;
        toastEl.classList.add("show");
        clearTimeout(toastTimer);
        toastTimer = setTimeout(function () { toastEl.classList.remove("show"); }, 2200);
    }

    // ---------- rendering ----------
    function coverHtml(book, withBadge) {
        const p = PALETTES[(Number(book.bookId) || 0) % PALETTES.length];
        return '<div class="book-cover" data-cover="' + escapeHtml(book.coverUrl || "") + '" data-id="' + book.bookId + '" ' +
            'style="--c1:' + p[0] + ';--c2:' + p[1] + '">' +
            (withBadge && book.newArrival ? '<span class="book-badge">New</span>' : "") +
            '<div class="cover-fallback"><div class="cover-title">' + escapeHtml(book.title) + '</div>' +
            '<div class="cover-author">' + escapeHtml(book.author) + '</div></div></div>';
    }

    function bookCardHtml(book) {
        const meta = [book.categoryName, book.genreName].filter(Boolean).join(" · ");
        return '<article class="book-card" data-id="' + book.bookId + '">' +
            coverHtml(book, true) +
            '<div class="book-title">' + escapeHtml(book.title) + '</div>' +
            '<div class="book-author">' + escapeHtml(book.author) + '</div>' +
            '<div class="book-meta">' + escapeHtml(meta || "Uncategorised") + '</div>' +
            '<div class="book-price">' + money(book.price) + '</div>' +
            '<div class="book-actions">' +
            '<button type="button" class="btn-store primary btn-details" data-id="' + book.bookId + '">View Details</button>' +
            '<button type="button" class="btn-store outline btn-cart" data-id="' + book.bookId + '">Add to Cart</button>' +
            '</div></article>';
    }

    function sectionHtml(title, books, countLabel) {
        return '<section class="store-card"><div class="store-card-head"><h2>' + escapeHtml(title) + '</h2>' +
            '<span class="count">' + escapeHtml(countLabel) + '</span></div>' +
            (books.length
                ? '<div class="book-grid">' + books.map(bookCardHtml).join("") + '</div>'
                : '<div class="store-empty">No books in this category yet.</div>') +
            '</section>';
    }

    function matchesQuery(book) {
        if (!query) return true;
        const q = query.toLowerCase();
        return (book.title || "").toLowerCase().indexOf(q) >= 0 ||
            (book.author || "").toLowerCase().indexOf(q) >= 0;
    }

    function renderChips() {
        let html = '<button type="button" class="chip' + (activeCategoryId === null ? " active" : "") + '" data-id="">' +
            '<i class="fas fa-border-all"></i>All</button>';
        catalog.categories.forEach(function (c) {
            html += '<button type="button" class="chip' + (activeCategoryId === c.categoryId ? " active" : "") +
                '" data-id="' + c.categoryId + '" title="' + escapeHtml(c.description || "") + '">' +
                '<i class="fas ' + chipIcon(c.categoryName) + '"></i>' + escapeHtml(c.categoryName) + '</button>';
        });
        chipRow.innerHTML = html;
        chipRow.querySelectorAll(".chip").forEach(function (btn) {
            btn.addEventListener("click", function () {
                const id = btn.getAttribute("data-id");
                activeCategoryId = id ? Number(id) : null;
                renderChips();
                renderSections();
            });
        });
    }

    function renderSections() {
        const visible = catalog.books.filter(matchesQuery);
        let html = "";

        if (activeCategoryId === null) {
            const arrivals = visible.filter(function (b) { return b.newArrival; });
            if (arrivals.length) html += sectionHtml("New Arrivals", arrivals, arrivals.length + " books");

            catalog.categories.forEach(function (c) {
                const inCat = visible.filter(function (b) { return b.categoryId === c.categoryId; });
                if (inCat.length || !query) html += sectionHtml(c.categoryName, inCat, String(inCat.length));
            });

            html += sectionHtml("All Books", visible, visible.length + " books");
        } else {
            const cat = catalog.categories.find(function (c) { return c.categoryId === activeCategoryId; });
            const inCat = visible.filter(function (b) { return b.categoryId === activeCategoryId; });
            html += sectionHtml(cat ? cat.categoryName : "Category", inCat, inCat.length + " books");
        }

        if (!visible.length) {
            html = '<section class="store-card"><div class="store-empty">No books match "' + escapeHtml(query) + '".</div></section>';
        }

        sectionsEl.innerHTML = html;
        bindBookActions(sectionsEl);
        loadCovers(sectionsEl);
    }

    // Use the real cover image when the file exists; otherwise keep the generated one.
    function loadCovers(root) {
        root.querySelectorAll(".book-cover[data-cover]").forEach(function (el) {
            const url = el.getAttribute("data-cover");
            if (!url) return;
            const img = new Image();
            img.onload = function () {
                el.style.backgroundImage = "url('" + url + "')";
                el.classList.add("has-image");
            };
            img.src = url;
        });
    }

    function findBook(id) {
        return catalog.books.find(function (b) { return String(b.bookId) === String(id); });
    }

    function bindBookActions(root) {
        root.querySelectorAll(".btn-details, .book-cover").forEach(function (el) {
            el.addEventListener("click", function () { openModal(findBook(el.getAttribute("data-id"))); });
        });
        root.querySelectorAll(".btn-cart").forEach(function (btn) {
            btn.addEventListener("click", function () { addToCart(findBook(btn.getAttribute("data-id"))); });
        });
        root.querySelectorAll(".btn-wish").forEach(function (btn) {
            btn.addEventListener("click", function () { toggleWishlist(findBook(btn.getAttribute("data-id"))); });
        });
    }

    function openModal(book) {
        if (!book) return;
        const meta = [book.categoryName, book.genreName].filter(Boolean).join(" · ");
        modal.innerHTML =
            '<button type="button" class="modal-close" id="modalClose" aria-label="Close"><i class="fas fa-times"></i></button>' +
            coverHtml(book, true) +
            '<div><h2 id="modalTitle">' + escapeHtml(book.title) + '</h2>' +
            '<div class="book-author">by ' + escapeHtml(book.author) + '</div>' +
            '<div class="book-meta">' + escapeHtml(meta || "Uncategorised") + ' &nbsp;·&nbsp; Book #' + book.bookId + '</div>' +
            '<div class="book-price">' + money(book.price) + '</div>' +
            '<p class="blurb">' + escapeHtml(book.blurb || "") + '</p>' +
            '<div class="book-actions">' +
            '<button type="button" class="btn-store primary btn-cart" data-id="' + book.bookId + '"><i class="fas fa-cart-plus"></i>&nbsp; Add to Cart</button>' +
            '<button type="button" class="btn-store outline btn-wish" data-id="' + book.bookId + '"><i class="far fa-heart"></i>&nbsp; Wishlist</button>' +
            '</div></div>';
        modalBackdrop.classList.add("open");
        document.getElementById("modalClose").addEventListener("click", closeModal);
        modal.querySelectorAll(".book-cover").forEach(function (el) { el.style.cursor = "default"; });
        modal.querySelectorAll(".btn-cart").forEach(function (btn) {
            btn.addEventListener("click", function () { addToCart(book); });
        });
        modal.querySelectorAll(".btn-wish").forEach(function (btn) {
            btn.addEventListener("click", function () { toggleWishlist(book); });
        });
        loadCovers(modal);
    }

    function closeModal() { modalBackdrop.classList.remove("open"); }
    modalBackdrop.addEventListener("click", function (e) { if (e.target === modalBackdrop) closeModal(); });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape") closeModal(); });

    // ---------- search (both boxes drive the same filter) ----------
    const MAX_QUERY = 80;
    function setQuery(q) {
        query = (q || "").trim().slice(0, MAX_QUERY);
        document.getElementById("storeSearchInput").value = query;
        document.getElementById("bandSearchInput").value = query;
        activeCategoryId = null;
        renderChips();
        renderSections();
    }
    ["storeSearchForm", "bandSearchForm"].forEach(function (id) {
        document.getElementById(id).addEventListener("submit", function (e) {
            e.preventDefault();
            setQuery(this.querySelector("input").value);
        });
    });
    document.getElementById("storeSearchInput").addEventListener("input", function () {
        query = this.value.trim().slice(0, MAX_QUERY);
        renderSections();
    });
    ["storeSearchInput", "bandSearchInput"].forEach(function (id) {
        document.getElementById(id).setAttribute("maxlength", String(MAX_QUERY));
    });

    document.getElementById("cartLink").addEventListener("click", function (e) {
        e.preventDefault();
        const n = readList("rp_cart").reduce(function (s, i) { return s + (i.qty || 1); }, 0);
        toast(n ? n + " item(s) in your cart — checkout arrives with the online store" : "Your cart is empty");
    });
    document.getElementById("wishlistLink").addEventListener("click", function (e) {
        e.preventDefault();
        const n = readList("rp_wishlist").length;
        toast(n ? n + " book(s) in your wishlist" : "Your wishlist is empty");
    });

    // ---------- mobile nav ----------
    const toggle = document.getElementById("navToggle");
    const nav = document.getElementById("siteNav");
    toggle.addEventListener("click", function () {
        const open = nav.classList.toggle("open");
        toggle.setAttribute("aria-expanded", open ? "true" : "false");
    });

    // ---------- load ----------
    updateBadges();
    fetch("/api/public/catalog")
        .then(function (r) { return r.json(); })
        .then(function (res) {
            catalog = (res && res.data) || catalog;
            renderChips();
            renderSections();
        })
        .catch(function () {
            sectionsEl.innerHTML = '<section class="store-card"><div class="store-empty">Could not load the catalogue. Please try again.</div></section>';
        });
})();

(function () {
    "use strict";

    const API = "/api/public/catalog";
    let allBooks = [];
    let categories = [];
    let activeCategoryId = "";

    const searchInput = document.getElementById("storeSearch");
    const topCategories = document.getElementById("topCategories");
    const newArrivals = document.getElementById("newArrivals");
    const categorySections = document.getElementById("categorySections");
    const allBooksEl = document.getElementById("allBooks");
    const allCount = document.getElementById("allCount");
    const allEmpty = document.getElementById("allEmpty");
    const sectionSearch = document.getElementById("sectionSearch");
    const searchResults = document.getElementById("searchResults");
    const searchCount = document.getElementById("searchCount");
    const searchEmpty = document.getElementById("searchEmpty");
    const sectionNew = document.getElementById("sectionNew");
    const sectionAll = document.getElementById("sectionAll");

    function escapeHtml(s) {
        return String(s == null ? "" : s)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }

    function priceBlock(book) {
        var disc = Number(book.discountPercent) || 0;
        var effective = book.price;
        var original = book.originalPrice != null ? book.originalPrice : book.price;
        if (disc > 0) {
            return '<div class="book-price-wrap">' +
                '<span class="discount-badge">-' + Math.round(disc) + '%</span>' +
                '<div class="book-card-price">' + money(effective) + '</div>' +
                '<div class="book-card-price-old">' + money(original) + '</div></div>';
        }
        return '<div class="book-card-price">' + money(effective) + '</div>';
    }
    function money(v) {
        if (v == null || v === "") return "—";
        return "Rs. " + Number(v).toLocaleString(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }

    function coverBlock(book) {
        if (book.coverImage) {
            return '<img src="' + escapeHtml(book.coverImage) + '" alt="' +
                escapeHtml(book.title || "Book cover") + '" loading="lazy">';
        }
        return '<div class="book-cover-fallback"><i class="fas fa-book"></i></div>';
    }

    function bookCard(book) {
        const title = escapeHtml(book.title || "Untitled");
        const author = escapeHtml(book.authorName || "Unknown author");
        const meta = escapeHtml(
            [book.categoryName, book.genreName].filter(Boolean).join(" · ")
        );
        return (
            '<article class="book-card" data-id="' + book.bookId + '">' +
            '<div class="book-cover-wrap">' +
            '<a class="book-cover-link" href="/book.html?id=' + book.bookId + '">' +
            coverBlock(book) +
            "</a>" +
            '<div class="cover-actions">' +
            '<button type="button" class="cover-btn btn-wishlist" data-id="' + book.bookId +
            '" title="Add to wishlist" aria-label="Add to wishlist"><i class="far fa-heart"></i></button>' +
            '<a class="cover-btn btn-view" href="/book.html?id=' + book.bookId +
            '" title="View details" aria-label="View details"><i class="far fa-eye"></i></a>' +
            '<button type="button" class="cover-btn btn-quick-cart" data-id="' + book.bookId +
            '" title="Add to cart" aria-label="Add to cart"><i class="fas fa-shopping-bag"></i></button>' +
            "</div></div>" +
            '<div class="book-card-body">' +
            '<h3 class="book-card-title"><a href="/book.html?id=' + book.bookId +
            '" style="color:inherit;text-decoration:none;">' + title + "</a></h3>" +
            '<p class="book-card-author">' + author + "</p>" +
            (meta ? '<p class="book-card-meta">' + meta + "</p>" : "") +
            priceBlock(book) +
            "</div></article>"
        );
    }

    function matchesQuery(book, q) {
        if (!q) return true;
        const hay = [
            book.title,
            book.authorName,
            book.isbn,
            book.categoryName,
            book.genreName
        ].join(" ").toLowerCase();
        return hay.indexOf(q) !== -1;
    }

    function filteredBooks() {
        const q = (searchInput.value || "").toLowerCase().trim();
        return allBooks.filter(function (b) {
            if (activeCategoryId && String(b.categoryId) !== String(activeCategoryId)) {
                return false;
            }
            return matchesQuery(b, q);
        });
    }

    function bindCartButtons(root) {
        if (!root) return;
        root.querySelectorAll(".btn-quick-cart").forEach(function (btn) {
            btn.addEventListener("click", function (e) {
                e.preventDefault();
                e.stopPropagation();
                const id = Number(btn.getAttribute("data-id"));
                const book = allBooks.find(function (b) { return Number(b.bookId) === id; });
                if (!book) return;
                if (window.RpCart) RpCart.add(book, 1);
                btn.classList.add("added");
                const icon = btn.querySelector("i");
                if (icon) { icon.className = "fas fa-check"; }
                setTimeout(function () {
                    btn.classList.remove("added");
                    if (icon) icon.className = "fas fa-shopping-bag";
                }, 1000);
            });
        });
        root.querySelectorAll(".btn-wishlist").forEach(function (btn) {
            btn.addEventListener("click", function (e) {
                e.preventDefault();
                e.stopPropagation();
                // Wishlist storage (simple local list)
                var id = Number(btn.getAttribute("data-id"));
                var list = [];
                try {
                    list = JSON.parse(localStorage.getItem("rp_wishlist") || "[]");
                    if (!Array.isArray(list)) list = [];
                } catch (err) { list = []; }
                if (list.indexOf(id) === -1) list.push(id);
                localStorage.setItem("rp_wishlist", JSON.stringify(list));
                var icon = btn.querySelector("i");
                if (icon) { icon.className = "fas fa-heart"; }
                var badge = document.getElementById("wishlistBadge");
                if (badge) badge.textContent = String(list.length);
            });
        });
    }

    function renderCategories() {
        if (!categories.length) {
            topCategories.innerHTML = '<div class="store-loading">No categories yet.</div>';
            return;
        }
        const icons = ["fa-book", "fa-feather", "fa-graduation-cap", "fa-child", "fa-flask", "fa-landmark", "fa-heart", "fa-globe"];
        let html = '<button type="button" class="cat-chip' + (!activeCategoryId ? " active" : "") +
            '" data-id=""><i class="fas fa-border-all"></i><span>All</span></button>';
        categories.forEach(function (c, idx) {
            html += '<button type="button" class="cat-chip' +
                (String(activeCategoryId) === String(c.categoryId) ? " active" : "") +
                '" data-id="' + c.categoryId + '">' +
                '<i class="fas ' + icons[idx % icons.length] + '"></i>' +
                "<span>" + escapeHtml(c.categoryName) + "</span></button>";
        });
        topCategories.innerHTML = html;
        topCategories.querySelectorAll(".cat-chip").forEach(function (chip) {
            chip.addEventListener("click", function () {
                activeCategoryId = chip.getAttribute("data-id") || "";
                render();
            });
        });
    }

    function render() {
        const q = (searchInput.value || "").toLowerCase().trim();
        const list = filteredBooks();

        // Search mode: focus results section
        if (q) {
            sectionSearch.hidden = false;
            sectionNew.hidden = true;
            categorySections.hidden = true;
            sectionAll.hidden = true;
            searchCount.textContent = list.length + (list.length === 1 ? " book" : " books");
            if (!list.length) {
                searchResults.innerHTML = "";
                searchEmpty.hidden = false;
            } else {
                searchEmpty.hidden = true;
                searchResults.innerHTML = list.map(bookCard).join("");
                bindCartButtons(searchResults);
            }
            return;
        }

        sectionSearch.hidden = true;
        sectionNew.hidden = false;
        categorySections.hidden = false;
        sectionAll.hidden = false;

        // New arrivals: newest first (createdAt), fallback bookId
        const newest = allBooks.slice().sort(function (a, b) {
            const ta = a.createdAt ? new Date(a.createdAt).getTime() : (a.bookId || 0);
            const tb = b.createdAt ? new Date(b.createdAt).getTime() : (b.bookId || 0);
            return tb - ta;
        }).filter(function (b) {
            return !activeCategoryId || String(b.categoryId) === String(activeCategoryId);
        }).slice(0, 12);

        newArrivals.innerHTML = newest.length
            ? newest.map(bookCard).join("")
            : '<p class="store-empty">No books available yet.</p>';
        bindCartButtons(newArrivals);

        // Category sections (only categories that have books)
        let catHtml = "";
        categories.forEach(function (c) {
            if (activeCategoryId && String(activeCategoryId) !== String(c.categoryId)) return;
            const books = allBooks.filter(function (b) {
                return String(b.categoryId) === String(c.categoryId);
            });
            if (!books.length) return;
            catHtml +=
                '<section class="store-section">' +
                '<div class="section-head"><h2>' + escapeHtml(c.categoryName) + "</h2>" +
                '<span class="section-count">' + books.length + "</span></div>" +
                '<div class="book-row">' + books.map(bookCard).join("") + "</div></section>";
        });
        categorySections.innerHTML = catHtml;
        bindCartButtons(categorySections);

        // All books grid
        allCount.textContent = list.length + (list.length === 1 ? " book" : " books");
        if (!list.length) {
            allBooksEl.innerHTML = "";
            allEmpty.hidden = false;
        } else {
            allEmpty.hidden = true;
            allBooksEl.innerHTML = list.map(bookCard).join("");
            bindCartButtons(allBooksEl);
        }

        renderCategories();
    }

    async function load() {
        try {
            const res = await fetch(API).then(function (r) { return r.json(); });
            const data = (res && res.data) || {};
            categories = data.categories || [];
            allBooks = (data.books || []).map(RpBooks.fromCatalog);

            // URL query support: ?q= or ?author=
            const params = new URLSearchParams(window.location.search);
            if (params.get("q")) searchInput.value = params.get("q");
            if (params.get("author")) searchInput.value = params.get("author");
            if (params.get("categoryId")) activeCategoryId = params.get("categoryId");

            render();
        } catch (e) {
            allBooksEl.innerHTML = "";
            allEmpty.hidden = false;
            allEmpty.textContent = "Could not load books. Please try again later.";
            topCategories.innerHTML = '<div class="store-loading">Could not load categories.</div>';
        }
    }

    searchInput.addEventListener("input", function () {
        render();
    });

    load();
})();

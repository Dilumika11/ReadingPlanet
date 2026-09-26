(function () {
    "use strict";

    const grid = document.getElementById("authorGrid");
    const empty = document.getElementById("authorsEmpty");
    const search = document.getElementById("authorSearch");
    let authors = [];

    function escapeHtml(s) {
        return String(s == null ? "" : s)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }

    function authorCard(a) {
        const name = a.authorName || "Unknown Author";
        const href = "/store.html?author=" + encodeURIComponent(name);

        // Muses-like avatar: illustration fallback (no external dependency)
        const avatar =
            '<div class="author-avatar">' +
            '<div class="author-avatar-fallback">' +
            '<div class="avatar-circle"><i class="fas fa-user"></i></div>' +
            '<div class="avatar-book"><i class="fas fa-book-open"></i></div>' +
            "</div></div>";

        return (
            '<a class="author-item" href="' + href + '" title="Books by ' + escapeHtml(name) + '">' +
            avatar +
            '<p class="author-name">' + escapeHtml(name) + "</p>" +
            "</a>"
        );
    }

    function render() {
        const q = (search.value || "").toLowerCase().trim();
        let list = authors;
        if (q) {
            list = authors.filter(function (a) {
                return (a.authorName || "").toLowerCase().indexOf(q) !== -1;
            });
        }

        if (!list.length) {
            grid.innerHTML = "";
            empty.hidden = false;
            empty.textContent = q ? "No authors match your search." : "No authors available yet.";
            return;
        }

        empty.hidden = true;
        grid.innerHTML = list.map(authorCard).join("");
    }

    fetch("/api/public/authors")
        .then(function (r) { return r.json(); })
        .then(function (res) {
            authors = (res && res.data) || [];
            // Sort A–Z by name
            authors.sort(function (a, b) {
                return String(a.authorName || "").localeCompare(String(b.authorName || ""), undefined, {
                    sensitivity: "base"
                });
            });
            render();
        })
        .catch(function () {
            grid.innerHTML = "";
            empty.hidden = false;
            empty.textContent = "Could not load authors.";
        });

    search.addEventListener("input", render);
})();

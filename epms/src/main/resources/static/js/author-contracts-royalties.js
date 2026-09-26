(function () {
    "use strict";

    const token = localStorage.getItem("rp_token") || sessionStorage.getItem("rp_token");
    const userRaw = localStorage.getItem("rp_user") || sessionStorage.getItem("rp_user");

    if (!token || !userRaw) {
        window.location.replace("/author-login.html");
        return;
    }

    const headers = { "Authorization": "Bearer " + token, "Content-Type": "application/json" };
    const notice = document.getElementById("notice");

    function showNotice(message, type) {
        notice.textContent = message;
        notice.className = "notice " + type;
        window.scrollTo({ top: 0, behavior: "smooth" });
    }

    async function api(url, options) {
        const response = await fetch(url, Object.assign({ headers }, options || {}));
        if (response.status === 401) {
            localStorage.removeItem("rp_token");
            sessionStorage.removeItem("rp_token");
            window.location.replace("/author-login.html");
            throw new Error("Session expired");
        }
        const body = await response.json().catch(() => ({}));
        if (!response.ok || body.success === false) throw new Error(body.message || "Request failed");
        return body.data;
    }

    function escapeHtml(value) {
        return String(value == null ? "" : value)
            .replaceAll("&", "&amp;").replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;").replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    function renderAgreements(contracts) {
        const root = document.getElementById("agreements");
        if (!contracts || !contracts.length) {
            root.innerHTML = '<div class="empty">No publisher-issued agreement is available yet.</div>';
            return;
        }

        root.innerHTML = contracts.map(function (c) {
            const signed = String(c.status).toUpperCase() === "SIGNED";
            return `
                <article class="agreement">
                    <div class="agreement-head">
                        <div>
                            <div class="agreement-title">${escapeHtml(c.agreementTitle)}</div>
                            <div class="agreement-meta">${escapeHtml(c.manuscriptTitle || "Publishing agreement")}</div>
                        </div>
                        <span class="badge ${signed ? "signed" : ""}">${escapeHtml(c.status.replaceAll("_", " "))}</span>
                    </div>
                    <div class="terms">
                        <div class="term"><span>Royalty Rate</span><strong>${escapeHtml(c.royaltyRate)}%</strong></div>
                        <div class="term"><span>Effective Date</span><strong>${escapeHtml(c.effectiveDate || "—")}</strong></div>
                        <div class="term"><span>Issued</span><strong>${escapeHtml(c.issuedAt ? new Date(c.issuedAt).toLocaleString() : "—")}</strong></div>
                        <div class="term"><span>Signed</span><strong>${escapeHtml(c.signedAt ? new Date(c.signedAt).toLocaleString() : "Not signed")}</strong></div>
                    </div>
                    ${signed ? '<div class="finance-note"><i class="fas fa-circle-check"></i> This agreement has already been accepted and signed in the system.</div>' : `
                        <label class="sign-row"><input type="checkbox" class="agreement-check" data-id="${c.contractId}"> I have read and agree to this agreement.</label>
                        <button class="btn btn-primary sign-btn" data-id="${c.contractId}"><i class="fas fa-signature"></i> Accept &amp; Sign Agreement</button>
                    `}
                </article>`;
        }).join("");

        root.querySelectorAll(".sign-btn").forEach(function (button) {
            button.addEventListener("click", async function () {
                const id = button.dataset.id;
                const checkbox = root.querySelector('.agreement-check[data-id="' + id + '"]');
                if (!checkbox.checked) {
                    showNotice("Please confirm that you have read and agree to the agreement.", "error");
                    return;
                }
                button.disabled = true;
                try {
                    await api("/api/authors/contracts-royalties/" + id + "/sign", { method: "POST" });
                    showNotice("Agreement accepted and signed successfully.", "success");
                    await load();
                } catch (e) {
                    button.disabled = false;
                    showNotice(e.message, "error");
                }
            });
        });
    }

    function renderBank(details) {
        if (!details) return;
        document.getElementById("accountName").value = details.accountName || "";
        document.getElementById("bankName").value = details.bankName || "";
        document.getElementById("accountNumber").value = details.accountNumber || "";
        document.getElementById("branch").value = details.branch || "";
    }

    function renderNotifications(items) {
        const root = document.getElementById("notifications");
        if (!items || !items.length) {
            root.innerHTML = '<div class="empty">No finance notifications yet.</div>';
            return;
        }
        root.innerHTML = items.map(function (n) {
            return `<div class="notification"><strong>${escapeHtml(n.title)}</strong><p>${escapeHtml(n.message)}</p></div>`;
        }).join("");
    }

    async function load() {
        try {
            const data = await api("/api/authors/contracts-royalties");
            renderAgreements(data.contracts);
            renderBank(data.bankDetails);
            renderNotifications(data.notifications);
        } catch (e) {
            showNotice(e.message, "error");
        }
    }

    document.getElementById("bankForm").addEventListener("submit", async function (event) {
        event.preventDefault();
        const payload = {
            accountName: document.getElementById("accountName").value.trim(),
            bankName: document.getElementById("bankName").value.trim(),
            accountNumber: document.getElementById("accountNumber").value.trim(),
            branch: document.getElementById("branch").value.trim()
        };
        try {
            await api("/api/authors/contracts-royalties/bank-details", {
                method: "PUT",
                body: JSON.stringify(payload)
            });
            showNotice("Bank details updated. Finance has been notified.", "success");
            await load();
        } catch (e) {
            showNotice(e.message, "error");
        }
    });

    load();
})();

/**
 * Reading Planet – Shared Header & Footer loader
 * Loads /components/header.html and /components/footer.html
 * into #header-container and #footer-container
 */
(function () {
    "use strict";

    const HEADER_URL = "/components/header.html";
    const FOOTER_URL = "/components/footer.html";

    async function loadComponent(url, containerId) {
        const container = document.getElementById(containerId);
        if (!container) {
            console.warn(`Container #${containerId} not found.`);
            return false;
        }

        try {
            const response = await fetch(url, { cache: "no-cache" });
            if (!response.ok) {
                throw new Error(`HTTP ${response.status} while loading ${url}`);
            }
            const html = await response.text();
            container.innerHTML = html;
            return true;
        } catch (err) {
            console.error(`Failed to load component from ${url}:`, err);
            container.innerHTML = `<!-- Failed to load ${url} -->`;
            return false;
        }
    }

    async function loadSharedComponents() {
        const [headerLoaded, footerLoaded] = await Promise.all([
            loadComponent(HEADER_URL, "header-container"),
            loadComponent(FOOTER_URL, "footer-container")
        ]);

        // Initialize after HTML is injected
        if (headerLoaded && typeof window.initReadingPlanetHeader === "function") {
            window.initReadingPlanetHeader();
            (function loadCartAssets() {
                if (!document.querySelector('link[href="/css/cart-drawer.css"]')) {
                    var l = document.createElement("link");
                    l.rel = "stylesheet";
                    l.href = "/css/cart-drawer.css";
                    document.head.appendChild(l);
                }
                function addScript(src, cb) {
                    if (document.querySelector('script[src="' + src + '"]')) { if (cb) cb(); return; }
                    var s = document.createElement("script");
                    s.src = src;
                    s.onload = function () { if (cb) cb(); };
                    document.body.appendChild(s);
                }
                addScript("/js/cart-utils.js", function () {
                    addScript("/js/cart-drawer.js", function () {
                        try { document.dispatchEvent(new CustomEvent("rp-header-ready")); } catch (e) {}
                    });
                });
            })();
        }
        if (footerLoaded && typeof window.initReadingPlanetFooter === "function") {
            window.initReadingPlanetFooter();
        }

        // Optional: notify other scripts that components are ready
        document.dispatchEvent(new CustomEvent("rp-components-loaded", {
            detail: { header: headerLoaded, footer: footerLoaded }
        }));
    }

    // Start loading when DOM is ready
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", loadSharedComponents);
    } else {
        loadSharedComponents();
    }
})();
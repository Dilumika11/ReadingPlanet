/**
 * Reading Planet – Footer functionality
 * Call window.initReadingPlanetFooter() after the footer HTML is injected.
 */
(function () {
    "use strict";

    function initFooter() {
        // Update copyright year dynamically
        const yearEl = document.getElementById("rp-copyright-year");
        if (yearEl) {
            yearEl.textContent = new Date().getFullYear();
        }

        // Back to top button
        const backToTopBtn = document.getElementById("rpBackToTop");
        if (backToTopBtn) {
            backToTopBtn.addEventListener("click", () => {
                window.scrollTo({
                    top: 0,
                    behavior: "smooth"
                });
            });
        }
    }

    // Expose so the loader can call it after injecting the HTML
    window.initReadingPlanetFooter = initFooter;

    // Auto-init if the footer is already in the page
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", () => {
            if (document.getElementById("rp-copyright-year") || document.getElementById("rpBackToTop")) {
                initFooter();
            }
        });
    } else if (document.getElementById("rp-copyright-year") || document.getElementById("rpBackToTop")) {
        initFooter();
    }
})();
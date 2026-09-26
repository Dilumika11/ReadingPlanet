/**
 * Reading Planet EPMS
 * Author Dashboard
 *
 * Handles:
 * - AUTHOR authentication
 * - Logout
 * - Author name
 * - Manuscript summary cards
 * - Recent submissions
 * - Navigation to My Submissions
 *
 * Epic 1 + Epic 2 status labels (continuity B):
 * REVISION_REQUESTED / RESUBMITTED mapped so author never sees UNKNOWN.
 */

(function () {

    "use strict";


    /* =========================================================
       CONSTANTS
    ========================================================= */

    const LOGIN_PATH =
        "/author-login.html";

    const AUTHOR_ROLE =
        "AUTHOR";

    const PROFILE_PATH =
        "/author/profile.html";

    const SUBMISSIONS_PATH =
        "/author/my-submissions.html";

    const DETAILS_PATH =
        "/author/my-submission-details.html";


    /* =========================================================
       SESSION
    ========================================================= */

    function getToken() {

        return localStorage.getItem("rp_token") ||
            sessionStorage.getItem("rp_token");
    }


    function getUser() {

        const raw =
            localStorage.getItem("rp_user") ||
            sessionStorage.getItem("rp_user");


        if (!raw) {

            return null;
        }


        try {

            return JSON.parse(raw);

        } catch (error) {

            return null;
        }
    }


    function clearSession() {

        localStorage.removeItem("rp_token");
        localStorage.removeItem("rp_user");

        sessionStorage.removeItem("rp_token");
        sessionStorage.removeItem("rp_user");
    }


    function resolveRole(user) {

        if (!user) {

            return null;
        }


        if (user.role) {

            return String(
                user.role
            ).toUpperCase();
        }


        if (
            user.roles &&
            user.roles.length
        ) {

            return String(
                user.roles[0]
            ).toUpperCase();
        }


        return null;
    }


    /* =========================================================
       AUTH CHECK
    ========================================================= */

    const token =
        getToken();


    const user =
        getUser();


    if (
        !token ||
        !user ||
        resolveRole(user) !== AUTHOR_ROLE
    ) {

        clearSession();

        window.location.replace(
            LOGIN_PATH
        );

        return;
    }


    /* =========================================================
       AUTHOR NAME
    ========================================================= */

    const displayName =
        user.fullName ||
        user.email ||
        "Author";


    const welcomeName =
        document.getElementById(
            "welcomeName"
        );


    const headerName =
        document.getElementById(
            "headerUserName"
        );


    const headerRole =
        document.getElementById(
            "headerUserRole"
        );


    if (welcomeName) {

        welcomeName.textContent =
            displayName;
    }


    if (headerName) {

        headerName.textContent =
            displayName;
    }


    if (headerRole) {

        headerRole.textContent =
            resolveRole(user);
    }


    /* =========================================================
       LOGOUT
    ========================================================= */

    const logoutBtn =
        document.getElementById(
            "logoutBtn"
        );


    if (logoutBtn) {

        logoutBtn.addEventListener(
            "click",
            function () {

                clearSession();

                window.location.replace(
                    "/"
                );
            }
        );
    }


    /* =========================================================
       NAVIGATION
    ========================================================= */

    document
        .querySelectorAll(
            '[data-dashboard-link="profile"]'
        )
        .forEach(
            function (element) {

                element.setAttribute(
                    "href",
                    PROFILE_PATH
                );
            }
        );


    document
        .querySelectorAll(
            '[data-dashboard-link="submissions"]'
        )
        .forEach(
            function (element) {

                element.setAttribute(
                    "href",
                    SUBMISSIONS_PATH
                );
            }
        );


    /* =========================================================
       SUMMARY ELEMENTS
    ========================================================= */

    const statTotal =
        document.getElementById(
            "statTotal"
        );


    const statUnderReview =
        document.getElementById(
            "statUnderReview"
        );


    const statApproved =
        document.getElementById(
            "statApproved"
        );


    const statRevision =
        document.getElementById(
            "statRevision"
        );


    /* =========================================================
       SUMMARY CARD LINKS
    ========================================================= */

    const summaryLinks = {

        statCardTotal:
        SUBMISSIONS_PATH,

        statCardUnderReview:
            SUBMISSIONS_PATH +
            "?status=UNDER_REVIEW",

        statCardApproved:
            SUBMISSIONS_PATH +
            "?status=APPROVED",

        statCardRevision:
            SUBMISSIONS_PATH +
            "?status=REVISION_REQUIRED"
    };


    Object.keys(summaryLinks)
        .forEach(
            function (id) {

                const element =
                    document.getElementById(
                        id
                    );


                if (element) {

                    element.href =
                        summaryLinks[id];
                }
            }
        );


    /* =========================================================
       SUMMARY LOADING
    ========================================================= */

    async function loadSummary() {

        try {

            const response =
                await fetch(
                    "/api/manuscripts/summary",
                    {
                        method: "GET",

                        headers: {
                            "Authorization":
                                "Bearer " +
                                token
                        }
                    }
                );


            if (
                response.status === 401 ||
                response.status === 403
            ) {

                clearSession();

                window.location.replace(
                    LOGIN_PATH
                );

                return;
            }


            const result =
                await response.json();


            if (
                !response.ok ||
                !result.success ||
                !result.data
            ) {

                return;
            }


            if (statTotal) {

                statTotal.textContent =
                    result.data.total ?? 0;
            }


            if (statUnderReview) {

                statUnderReview.textContent =
                    result.data.underReview ?? 0;
            }


            if (statApproved) {

                statApproved.textContent =
                    result.data.approved ?? 0;
            }


            if (statRevision) {

                statRevision.textContent =
                    result.data.revisionRequired ?? 0;
            }


        } catch (error) {

            console.error(
                "Failed to load manuscript summary:",
                error
            );
        }
    }


    /* =========================================================
       RECENT SUBMISSIONS
    ========================================================= */

    const submissionsBody =
        document.getElementById(
            "submissionsBody"
        );


    function formatDate(
        value
    ) {

        if (!value) {

            return "—";
        }


        const date =
            new Date(value);


        if (
            Number.isNaN(
                date.getTime()
            )
        ) {

            return "—";
        }


        return date.toLocaleDateString(
            "en-GB",
            {
                day: "2-digit",
                month: "short",
                year: "numeric"
            }
        );
    }


    /**
     * Epic 1 statuses kept.
     * Epic 2 statuses added so author never sees UNKNOWN
     * after Request Revision / resubmit (continuity B).
     */
    function getStatusClass(
        status
    ) {

        switch (
            String(status || "")
                .toUpperCase()
            ) {

            case "APPROVED":
            case "EDITORIALLY_ACCEPTED":
            case "ACCEPTED":
            case "IN_DESIGN":
            case "AWAITING_AUTHOR_APPROVAL":
            case "DESIGN_APPROVED":
            case "QC_PASSED":
            case "READY_FOR_PRINTING":
            case "PUBLISHED":
                return "dashboard-status-approved";

            case "UNDER_REVIEW":
            case "ASSIGNED":
            case "RESUBMITTED":
            case "REVISED_SUBMITTED":
                return "dashboard-status-review";

            case "REVISION_REQUIRED":
            case "REVISION_REQUESTED":
            case "REJECTED":
                return "dashboard-status-revision";

            case "SUBMITTED":
            default:
                return "dashboard-status-submitted";
        }
    }


    function getStatusLabel(
        status
    ) {

        switch (
            String(status || "")
                .toUpperCase()
            ) {

            case "APPROVED":
                return "APPROVED";

            case "EDITORIALLY_ACCEPTED":
                return "EDITORIALLY ACCEPTED";

            case "UNDER_REVIEW":
            case "ASSIGNED":
                return "UNDER REVIEW";

            case "REVISION_REQUIRED":
            case "REVISION_REQUESTED":
                return "REVISION REQUIRED";

            case "RESUBMITTED":
            case "REVISED_SUBMITTED":
                return "RESUBMITTED";

            case "REJECTED":
                return "REJECTED";

            case "SUBMITTED":
                return "SUBMITTED";

            default:
                return String(status || "SUBMITTED")
                    .replaceAll("_", " ");
        }
    }


    function escapeHtml(
        value
    ) {

        return String(
            value ?? ""
        )
            .replaceAll(
                "&",
                "&amp;"
            )
            .replaceAll(
                "<",
                "&lt;"
            )
            .replaceAll(
                ">",
                "&gt;"
            )
            .replaceAll(
                '"',
                "&quot;"
            )
            .replaceAll(
                "'",
                "&#039;"
            );
    }


    function renderRecentSubmissions(
        manuscripts
    ) {

        if (!submissionsBody) {

            return;
        }


        if (
            !manuscripts ||
            manuscripts.length === 0
        ) {

            submissionsBody.innerHTML = `
                <tr>
                    <td
                        colspan="4"
                        class="dashboard-table-empty"
                    >
                        <div class="dashboard-empty-content">

                            <i class="far fa-file-alt"></i>

                            <strong>
                                No submissions yet
                            </strong>

                            <span>
                                Use Submit Manuscript to send your first work.
                            </span>

                        </div>
                    </td>
                </tr>
            `;

            return;
        }


        submissionsBody.innerHTML =
            manuscripts
                .slice(0, 5)
                .map(
                    function (item) {

                        const status =
                            String(
                                item.status ||
                                ""
                            ).toUpperCase();


                        return `
                            <tr>

                                <td>

                                    <div class="dashboard-manuscript-title">

                                        <span class="dashboard-manuscript-code">
                                            ${escapeHtml(
                            item.manuscriptCode ||
                            "N/A"
                        )}
                                        </span>

                                        <strong>
                                            ${escapeHtml(
                            item.title ||
                            "Untitled Manuscript"
                        )}
                                        </strong>

                                    </div>

                                </td>


                                <td>
                                    ${formatDate(
                            item.submittedAt
                        )}
                                </td>


                                <td>

                                    <span
                                        class="dashboard-status
                                        ${getStatusClass(status)}"
                                    >

                                        <span
                                            class="dashboard-status-dot"
                                        ></span>

                                        ${getStatusLabel(status)}

                                    </span>

                                </td>


                                <td>

                                    <a
                                        href="${DETAILS_PATH}?manuscriptId=${encodeURIComponent(
                            item.manuscriptId
                        )}"
                                        class="dashboard-view-link"
                                    >
                                        View
                                        <i class="fas fa-arrow-right"></i>
                                    </a>

                                </td>

                            </tr>
                        `;
                    }
                )
                .join("");
    }


    async function loadRecentSubmissions() {

        try {

            const response =
                await fetch(
                    "/api/manuscripts/my-submissions",
                    {
                        method: "GET",

                        headers: {
                            "Authorization":
                                "Bearer " +
                                token
                        }
                    }
                );


            if (
                response.status === 401 ||
                response.status === 403
            ) {

                clearSession();

                window.location.replace(
                    LOGIN_PATH
                );

                return;
            }


            const result =
                await response.json();


            if (
                !response.ok ||
                !result.success
            ) {

                renderRecentSubmissions(
                    []
                );

                return;
            }


            renderRecentSubmissions(
                result.data || []
            );


        } catch (error) {

            console.error(
                "Failed to load recent submissions:",
                error
            );


            renderRecentSubmissions(
                []
            );
        }
    }


    /* =========================================================
       INITIAL LOAD
    ========================================================= */

    loadSummary();

    loadRecentSubmissions();

})();
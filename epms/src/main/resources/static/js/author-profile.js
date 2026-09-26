(function () {

    "use strict";


    /* =====================================================
       AUTHENTICATION
       ===================================================== */

    const token =
        localStorage.getItem("rp_token") ||
        sessionStorage.getItem("rp_token");


    const storedUser =
        localStorage.getItem("rp_user") ||
        sessionStorage.getItem("rp_user");


    if (!token || !storedUser) {

        window.location.href =
            "/author-login.html";

        return;
    }


    let user;


    try {

        user =
            JSON.parse(storedUser);

    } catch (error) {

        localStorage.removeItem(
            "rp_token"
        );

        localStorage.removeItem(
            "rp_user"
        );

        sessionStorage.removeItem(
            "rp_token"
        );

        sessionStorage.removeItem(
            "rp_user"
        );

        window.location.href =
            "/author-login.html";

        return;
    }



    /* =====================================================
       ROLE CHECK
       ===================================================== */

    const role =
        user.role ||
        user.userRole ||
        user.authority ||
        (Array.isArray(user.roles) && user.roles.length
            ? user.roles[0]
            : null);


    if (
        role &&
        String(role).toUpperCase() !== "AUTHOR"
    ) {

        window.location.href =
            "/author-login.html";

        return;
    }



    /* =====================================================
       ELEMENTS
       ===================================================== */

    const form =
        document.getElementById(
            "authorProfileForm"
        );


    const messageEl =
        document.getElementById(
            "profileMessage"
        );


    const saveBtn =
        document.getElementById(
            "saveProfileBtn"
        );


    const biography =
        document.getElementById(
            "biography"
        );


    const bioCount =
        document.getElementById(
            "bioCount"
        );



    /* =====================================================
       MESSAGE
       ===================================================== */

    function showMessage(
        message,
        type
    ) {

        if (!messageEl) {
            return;
        }

        messageEl.textContent =
            message;

        messageEl.hidden =
            false;

        messageEl.className =
            "ap-message " +
            (type || "");

    }


    function clearMessage() {

        if (!messageEl) {
            return;
        }

        messageEl.textContent =
            "";

        messageEl.hidden =
            true;

    }



    /* =====================================================
       LOADING BUTTON
       ===================================================== */

    function setLoading(
        loading
    ) {

        if (!saveBtn) {
            return;
        }

        saveBtn.disabled =
            loading;


        saveBtn.innerHTML =
            loading

                ? '<i class="fas fa-spinner fa-spin"></i> Saving...'

                : '<i class="fas fa-floppy-disk"></i> Save Changes';

    }



    /* =====================================================
       BIOGRAPHY COUNTER
       ===================================================== */

    function updateBioCount() {

        if (
            !biography ||
            !bioCount
        ) {

            return;
        }

        bioCount.textContent =
            biography.value.length;

    }



    /* =====================================================
       DATE FORMAT
       ===================================================== */

    function formatDateTime(
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

            return value;

        }


        return date.toLocaleString();

    }



    /* =====================================================
       LOAD PROFILE
       ===================================================== */

    async function loadProfile() {

        clearMessage();


        try {

            const response =
                await fetch(
                    "/api/authors/profile",
                    {
                        method: "GET",

                        headers: {

                            "Authorization":
                                "Bearer " + token,

                            "Content-Type":
                                "application/json"

                        }

                    }
                );


            /* =================================================
               UNAUTHORIZED
               ================================================= */

            if (
                response.status === 401 ||
                response.status === 403
            ) {

                localStorage.removeItem(
                    "rp_token"
                );

                localStorage.removeItem(
                    "rp_user"
                );

                sessionStorage.removeItem(
                    "rp_token"
                );

                sessionStorage.removeItem(
                    "rp_user"
                );

                window.location.href =
                    "/author-login.html";

                return;
            }


            const result =
                await response.json();


            if (
                !response.ok ||
                !result.success
            ) {

                throw new Error(
                    result.message ||
                    "Unable to load author profile"
                );

            }


            const profile =
                result.data || {};


            /* =================================================
               AUTHOR CODE
               ================================================= */

            /*
             * Backend eken authorCode awoth
             * eka use karanawa.
             *
             * authorCode eka missing nam
             * userId eken automatic fallback ekak
             * hadanawa.
             */

            const resolvedAuthorCode =
                profile.authorCode ||
                (
                    profile.userId
                        ? "AUTH-" + profile.userId
                        : (
                            user.userId || user.id
                                ? "AUTH-" +
                                (user.userId || user.id)
                                : ""
                        )
                );


            /*
             * Profile object ekata resolved code eka
             * set karanawa so pahalata thiyena
             * anith sections walatath same value eka use wenawa.
             */

            profile.authorCode =
                resolvedAuthorCode;



            /* =================================================
               FORM VALUES
               ================================================= */

            const authorCodeInput =
                document.getElementById(
                    "authorCode"
                );


            if (authorCodeInput) {

                authorCodeInput.value =
                    resolvedAuthorCode || "";

            }


            const statusInput =
                document.getElementById(
                    "status"
                );


            if (statusInput) {

                statusInput.value =
                    profile.status || "";

            }


            const fullNameInput =
                document.getElementById(
                    "fullName"
                );


            if (fullNameInput) {

                fullNameInput.value =
                    profile.fullName || "";

            }


            const emailInput =
                document.getElementById(
                    "email"
                );


            if (emailInput) {

                emailInput.value =
                    profile.email || "";

            }


            const penNameInput =
                document.getElementById(
                    "penName"
                );


            if (penNameInput) {

                penNameInput.value =
                    profile.penName || "";

            }


            const dateOfBirthInput =
                document.getElementById(
                    "dateOfBirth"
                );


            if (dateOfBirthInput) {

                dateOfBirthInput.value =
                    profile.dateOfBirth || "";

            }


            const nationalityInput =
                document.getElementById(
                    "nationality"
                );


            if (nationalityInput) {

                nationalityInput.value =
                    profile.nationality || "";

            }

            const phoneInput = document.getElementById("phoneNumber");
            if (phoneInput) {
                phoneInput.value = profile.phoneNumber || "";
            }


            const websiteInput =
                document.getElementById(
                    "website"
                );


            if (websiteInput) {

                websiteInput.value =
                    profile.website || "";

            }


            if (biography) {

                biography.value =
                    profile.biography || "";

            }



            /* =================================================
               SIDEBAR
               ================================================= */

            const sidebarFullName =
                document.getElementById(
                    "sidebarFullName"
                );


            if (sidebarFullName) {

                sidebarFullName.textContent =
                    profile.fullName ||
                    user.fullName ||
                    "Author";

            }


            const sidebarAuthorCode =
                document.getElementById(
                    "sidebarAuthorCode"
                );


            if (sidebarAuthorCode) {

                sidebarAuthorCode.textContent =
                    resolvedAuthorCode ||
                    "Author Code";

            }


            const sidebarStatus =
                document.getElementById(
                    "sidebarStatus"
                );


            if (sidebarStatus) {

                sidebarStatus.textContent =
                    profile.status ||
                    "ACTIVE";

            }



            /* =================================================
               HEADER
               ================================================= */

            const headerAuthorName =
                document.getElementById(
                    "headerAuthorName"
                );


            if (headerAuthorName) {

                headerAuthorName.textContent =
                    profile.fullName ||
                    user.fullName ||
                    "Author";

            }



            /* =================================================
               DATES
               ================================================= */

            const createdAt =
                document.getElementById(
                    "createdAt"
                );


            if (createdAt) {

                createdAt.textContent =
                    formatDateTime(
                        profile.createdAt
                    );

            }


            const updatedAt =
                document.getElementById(
                    "updatedAt"
                );


            if (updatedAt) {

                updatedAt.textContent =
                    formatDateTime(
                        profile.updatedAt
                    );

            }


            updateBioCount();

        }


        catch (error) {

            console.error(
                "Profile loading error:",
                error
            );


            showMessage(
                error.message ||
                "Unable to load profile",
                "error"
            );

        }

    }



    /* =====================================================
       UPDATE PROFILE
       ===================================================== */

    if (form) {

        form.addEventListener(
            "submit",
            async function (event) {

                event.preventDefault();


                clearMessage();


                const data = {

                    penName:
                        document.getElementById(
                            "penName"
                        ).value.trim(),


                    biography:
                        biography
                            ? biography.value.trim()
                            : "",


                    dateOfBirth:
                        document.getElementById(
                            "dateOfBirth"
                        ).value ||
                        null,


                    nationality:
                        document.getElementById(
                            "nationality"
                        ).value.trim(),


                    website:
                        document.getElementById(
                            "website"
                        ).value.trim(),

                    phoneNumber:
                        (document.getElementById("phoneNumber") || { value: "" }).value.trim()

                };



                /* =================================================
                   WEBSITE VALIDATION
                   ================================================= */

                if (
                    data.website &&
                    !/^https?:\/\/.+/i.test(
                        data.website
                    )
                ) {

                    showMessage(
                        "Website must start with http:// or https://",
                        "error"
                    );

                    return;
                }



                /* =================================================
                   DATE OF BIRTH VALIDATION
                   ================================================= */

                if (
                    data.dateOfBirth
                ) {

                    const selectedDate =
                        new Date(
                            data.dateOfBirth
                        );


                    const today =
                        new Date();


                    today.setHours(
                        0,
                        0,
                        0,
                        0
                    );


                    if (
                        selectedDate > today
                    ) {

                        showMessage(
                            "Date of birth cannot be in the future",
                            "error"
                        );

                        return;
                    }

                }



                setLoading(true);


                try {

                    const response =
                        await fetch(
                            "/api/authors/profile",
                            {
                                method: "PUT",

                                headers: {

                                    "Authorization":
                                        "Bearer " +
                                        token,

                                    "Content-Type":
                                        "application/json"

                                },

                                body:
                                    JSON.stringify(
                                        data
                                    )

                            }
                        );


                    /* =================================================
                       UNAUTHORIZED
                       ================================================= */

                    if (
                        response.status === 401 ||
                        response.status === 403
                    ) {

                        localStorage.removeItem(
                            "rp_token"
                        );

                        localStorage.removeItem(
                            "rp_user"
                        );

                        sessionStorage.removeItem(
                            "rp_token"
                        );

                        sessionStorage.removeItem(
                            "rp_user"
                        );

                        window.location.href =
                            "/author-login.html";

                        return;
                    }


                    const result =
                        await response.json();


                    if (
                        !response.ok ||
                        !result.success
                    ) {

                        throw new Error(
                            result.message ||
                            "Unable to update profile"
                        );

                    }


                    showMessage(
                        "Profile updated successfully.",
                        "success"
                    );


                    /* =================================================
                       UPDATE RETURNED DATA
                       ================================================= */

                    if (
                        result.data
                    ) {

                        const returnedProfile =
                            result.data;


                        const returnedAuthorCode =
                            returnedProfile.authorCode ||
                            (
                                returnedProfile.userId
                                    ? "AUTH-" +
                                    returnedProfile.userId
                                    : ""
                            );


                        /* -----------------------------
                           Created Date
                           ----------------------------- */

                        const createdAt =
                            document.getElementById(
                                "createdAt"
                            );


                        if (createdAt) {

                            createdAt.textContent =
                                formatDateTime(
                                    returnedProfile.createdAt
                                );

                        }


                        /* -----------------------------
                           Updated Date
                           ----------------------------- */

                        const updatedAt =
                            document.getElementById(
                                "updatedAt"
                            );


                        if (updatedAt) {

                            updatedAt.textContent =
                                formatDateTime(
                                    returnedProfile.updatedAt
                                );

                        }


                        /* -----------------------------
                           Author Code Input
                           ----------------------------- */

                        const authorCodeInput =
                            document.getElementById(
                                "authorCode"
                            );


                        if (authorCodeInput) {

                            authorCodeInput.value =
                                returnedAuthorCode;

                        }


                        /* -----------------------------
                           Sidebar Author Code
                           ----------------------------- */

                        const sidebarAuthorCode =
                            document.getElementById(
                                "sidebarAuthorCode"
                            );


                        if (sidebarAuthorCode) {

                            sidebarAuthorCode.textContent =
                                returnedAuthorCode ||
                                "Author Code";

                        }


                        /* -----------------------------
                           Sidebar Status
                           ----------------------------- */

                        const sidebarStatus =
                            document.getElementById(
                                "sidebarStatus"
                            );


                        if (sidebarStatus) {

                            sidebarStatus.textContent =
                                returnedProfile.status ||
                                "ACTIVE";

                        }


                        /* -----------------------------
                           Header Name
                           ----------------------------- */

                        const headerAuthorName =
                            document.getElementById(
                                "headerAuthorName"
                            );


                        if (headerAuthorName) {

                            headerAuthorName.textContent =
                                returnedProfile.fullName ||
                                user.fullName ||
                                "Author";

                        }


                        /* -----------------------------
                           Sidebar Name
                           ----------------------------- */

                        const sidebarFullName =
                            document.getElementById(
                                "sidebarFullName"
                            );


                        if (sidebarFullName) {

                            sidebarFullName.textContent =
                                returnedProfile.fullName ||
                                user.fullName ||
                                "Author";

                        }

                    }

                }


                catch (error) {

                    console.error(
                        "Profile update error:",
                        error
                    );


                    showMessage(
                        error.message ||
                        "Unable to update profile",
                        "error"
                    );

                }


                finally {

                    setLoading(false);

                }

            }
        );

    }



    /* =====================================================
       BIOGRAPHY COUNTER
       ===================================================== */

    if (biography) {

        biography.addEventListener(
            "input",
            updateBioCount
        );

    }


    updateBioCount();


    /* =====================================================
       LOAD PROFILE
       ===================================================== */

    loadProfile();

})();
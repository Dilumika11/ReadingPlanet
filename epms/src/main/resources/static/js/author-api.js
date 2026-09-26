/*
 * Author portal API adapter.
 *
 * The author pages were written against an earlier API (/api/manuscripts/**,
 * /api/authors/**, /api/editorial-reviews/**). This file maps those calls onto
 * the EPMS author API (/api/author/**, /api/documents/**) and reshapes the
 * answers into the fields the pages read, so the pages need no other change.
 *
 * Load it before any other script on an author page.
 */
(function (global) {
    "use strict";

    var nativeFetch = global.fetch.bind(global);

    /** Statuses at or after editorial acceptance (Epic 2 and 3 stages). */
    var ACCEPTED_STAGES = ["ACCEPTED", "IN_DESIGN", "AWAITING_AUTHOR_APPROVAL", "DESIGN_APPROVED",
        "QC_PASSED", "READY_FOR_PRINTING", "PUBLISHED"];

    var ROUTES = [
        { method: "GET", re: /^\/api\/manuscripts\/summary$/, run: summary },
        { method: "GET", re: /^\/api\/manuscripts\/my-submissions$/, run: mySubmissions },
        { method: "POST", re: /^\/api\/manuscripts\/submit$/, run: submitManuscript },
        { method: "GET", re: /^\/api\/manuscripts\/(\d+)\/files\/(\d+)\/download$/, run: downloadFile },
        { method: "GET", re: /^\/api\/manuscripts\/(\d+)\/files$/, run: manuscriptFiles },
        { method: "POST", re: /^\/api\/manuscripts\/(\d+)\/revision$/, run: uploadRevision },
        { method: "GET", re: /^\/api\/manuscripts\/(\d+)$/, run: manuscriptDetail },
        { method: "DELETE", re: /^\/api\/manuscripts\/(\d+)$/, run: deleteDraft },
        { method: "GET", re: /^\/api\/editorial-reviews\/manuscript\/(\d+)$/, run: reviews },
        { method: "GET", re: /^\/api\/authors\/profile$/, run: getProfile },
        { method: "PUT", re: /^\/api\/authors\/profile$/, run: putProfile },
        { method: "GET", re: /^\/api\/author\/designs\/pending$/, run: pendingDesigns },
        { method: "GET", re: /^\/api\/author\/designs\/history$/, run: designHistory },
        { method: "POST", re: /^\/api\/author\/designs\/(\d+)\/decision$/, run: designDecision },
        { method: "GET", re: /^\/api\/author\/designs\/(\d+)\/download\/([\w-]+)$/, run: designDownload },
        { method: "GET", re: /^\/api\/authors\/contracts-royalties$/, run: pass("/api/author/contracts-royalties") },
        { method: "POST", re: /^\/api\/authors\/contracts-royalties\/(\d+)\/sign$/,
            run: function (c, m) { return forward(c, "/api/author/contracts/" + m[1] + "/sign"); } },
        { method: "PUT", re: /^\/api\/authors\/contracts-royalties\/bank-details$/, run: pass("/api/author/bank-details") }
    ];

    global.fetch = function (input, init) {
        var url = typeof input === "string" ? input : (input && input.url) || "";
        var method = ((init && init.method) || (input && input.method) || "GET").toUpperCase();
        var parsed = new URL(url, global.location.origin);
        if (parsed.origin !== global.location.origin) return nativeFetch(input, init);
        for (var i = 0; i < ROUTES.length; i++) {
            var r = ROUTES[i];
            var match = r.method === method && parsed.pathname.match(r.re);
            if (match) {
                var call = { init: init || {}, query: parsed.searchParams, headers: headersOf(init) };
                return Promise.resolve().then(function () { return r.run(call, match); })
                    .catch(function (e) { return json(e.status || 500, false, e.message || "Request failed", null); });
            }
        }
        return nativeFetch(input, init);
    };

    // ---------------------------------------------------------------- helpers

    function headersOf(init) {
        var h = {};
        var src = (init && init.headers) || {};
        if (typeof src.forEach === "function" && !(src instanceof Array)) {
            src.forEach(function (v, k) { h[k] = v; });
        } else {
            Object.keys(src).forEach(function (k) { h[k] = src[k]; });
        }
        if (!h.Authorization && !h.authorization) {
            var t = global.localStorage.getItem("rp_token") || global.sessionStorage.getItem("rp_token");
            if (t) h.Authorization = "Bearer " + t;
        }
        return h;
    }

    function authOnly(call) {
        var h = {};
        var a = call.headers.Authorization || call.headers.authorization;
        if (a) h.Authorization = a;
        return h;
    }

    function json(status, success, message, data) {
        return new Response(JSON.stringify({ success: success, message: message, data: data }), {
            status: status,
            headers: { "Content-Type": "application/json" }
        });
    }

    /** Calls the EPMS API; rejects with the server's message (and status) on failure. */
    function api(call, method, path, body) {
        var headers = authOnly(call);
        var payload = body;
        if (body != null && !(body instanceof FormData)) {
            headers["Content-Type"] = "application/json";
            payload = JSON.stringify(body);
        }
        return nativeFetch(path, { method: method, headers: headers, body: payload }).then(function (res) {
            return res.json().catch(function () { return {}; }).then(function (out) {
                if (!res.ok || out.success === false) {
                    var err = new Error(out.message || ("Request failed (" + res.status + ")"));
                    err.status = res.status >= 400 ? res.status : 400;
                    throw err;
                }
                return out.data;
            });
        });
    }

    function pass(path) {
        return function (call) { return forward(call, path); };
    }

    function forward(call, path) {
        return nativeFetch(path, Object.assign({}, call.init, { headers: call.headers }));
    }

    function fullName(first, last) {
        return [first, last].filter(Boolean).join(" ");
    }

    function currentUser() {
        try {
            return JSON.parse(global.localStorage.getItem("rp_user") || global.sessionStorage.getItem("rp_user") || "{}");
        } catch (e) {
            return {};
        }
    }

    // ---------------------------------------------------------------- manuscripts

    function row(v) {
        var m = v.manuscript || {};
        return {
            manuscriptId: m.manuscriptId,
            manuscriptCode: m.manuscriptCode,
            title: m.title,
            synopsis: m.synopsis,
            language: m.language,
            wordCount: m.wordCount,
            genreId: m.genreId,
            genreName: v.genreName,
            status: m.status,
            submittedAt: m.submittedAt || m.createdAt,
            createdAt: m.createdAt,
            updatedAt: m.updatedAt,
            authorName: v.authorName,
            editorName: v.editorName,
            revisionDeadline: v.revisionDeadline,
            revisionOverdue: v.revisionOverdue
        };
    }

    function summary(call) {
        return api(call, "GET", "/api/author/manuscripts").then(function (list) {
            var count = function (test) { return list.filter(function (v) { return test(v.manuscript.status); }).length; };
            return json(200, true, "Manuscript summary", {
                total: list.length,
                drafts: count(function (s) { return s === "DRAFT"; }),
                underReview: count(function (s) { return s === "SUBMITTED" || s === "UNDER_REVIEW" || s === "RESUBMITTED"; }),
                revisionRequired: count(function (s) { return s === "REVISION_REQUESTED"; }),
                approved: count(function (s) { return ACCEPTED_STAGES.indexOf(s) !== -1; }),
                rejected: count(function (s) { return s === "REJECTED"; })
            });
        });
    }

    function mySubmissions(call) {
        var status = (call.query.get("status") || "").toUpperCase();
        return api(call, "GET", "/api/author/manuscripts").then(function (list) {
            var rows = list.map(row).filter(function (m) { return !status || m.status === status; });
            return json(200, true, "Your manuscripts", rows);
        });
    }

    /**
     * EPMS numbers every upload; the pages expect a version to mean "a copy of
     * the manuscript", with supporting files sharing the version they came with.
     */
    function fileViews(manuscriptId, files) {
        var byUpload = files.slice().sort(function (a, b) {
            return (a.fileVersion || 0) - (b.fileVersion || 0);
        });
        var version = 0;
        return byUpload.map(function (f) {
            if (f.fileCategory === "MANUSCRIPT") version += 1;
            return {
                fileId: f.manuscriptFileId,
                manuscriptFileId: f.manuscriptFileId,
                fileName: f.fileName,
                fileType: f.fileType,
                fileSize: f.fileSize,
                fileVersion: Math.max(version, 1),
                uploadNumber: f.fileVersion,
                fileCategory: f.fileCategory,
                notes: f.notes,
                uploadedAt: f.uploadedAt,
                downloadUrl: "/api/manuscripts/" + manuscriptId + "/files/" + f.manuscriptFileId + "/download"
            };
        });
    }

    function detailView(d) {
        var m = d.manuscript;
        var people = d.people || {};
        var revisions = (d.revisions || []).map(function (r) {
            return {
                revisionId: r.revisionId,
                revisionRound: r.revisionRound,
                comments: r.editorComments,
                editorComments: r.editorComments,
                deadline: r.responseDeadline,
                responseDeadline: r.responseDeadline,
                status: r.status,
                createdAt: r.createdAt,
                respondedAt: r.respondedAt,
                responseNotes: r.responseNotes,
                requestedBy: people[r.reviewerId] || null
            };
        });
        var reviews = (d.reviews || []).map(function (r) {
            var rev = revisions.filter(function (x) { return x.revisionRound === r.reviewRound; })[0];
            return {
                reviewId: r.reviewId,
                reviewerName: people[r.reviewerId] || "Editor",
                reviewRound: r.reviewRound,
                decision: r.decision,
                comments: r.reviewComments,
                reviewComments: r.reviewComments,
                reviewedAt: r.reviewedAt,
                responseDeadline: rev ? rev.responseDeadline : null
            };
        });
        var files = fileViews(m.manuscriptId, d.files || []);
        var versions = files.filter(function (f) { return f.fileCategory === "MANUSCRIPT"; })
            .map(function (f) { return f.fileVersion || 0; });
        var out = row(d);
        out.penName = null;
        out.files = files;
        out.reviews = reviews;
        out.revisions = revisions;
        out.history = d.history || [];
        out.latestFileVersion = versions.length ? Math.max.apply(null, versions) : 0;
        return out;
    }

    function manuscriptDetail(call, m) {
        return api(call, "GET", "/api/author/manuscripts/" + m[1]).then(function (d) {
            return json(200, true, "Manuscript", detailView(d));
        });
    }

    function manuscriptFiles(call, m) {
        return api(call, "GET", "/api/author/manuscripts/" + m[1]).then(function (d) {
            return json(200, true, "Manuscript files", detailView(d).files);
        });
    }

    function reviews(call, m) {
        return api(call, "GET", "/api/author/manuscripts/" + m[1]).then(function (d) {
            return json(200, true, "Editorial reviews", detailView(d).reviews);
        });
    }

    function downloadFile(call, m) {
        return nativeFetch("/api/documents/manuscript-files/" + m[2], { headers: authOnly(call) });
    }

    function deleteDraft(call, m) {
        return api(call, "DELETE", "/api/author/manuscripts/" + m[1]).then(function () {
            return json(200, true, "Draft deleted", null);
        });
    }

    function uploadRevision(call, m) {
        var form = call.init.body;
        return api(call, "POST", "/api/author/manuscripts/" + m[1] + "/revision", form).then(function (d) {
            return json(200, true, "Revised manuscript uploaded and sent back to the editor.", d);
        });
    }

    /**
     * The old API took the details and files in one request. EPMS creates a
     * draft, uploads the files, then submits; a failed attempt removes the draft
     * so the author can simply try again.
     */
    function submitManuscript(call) {
        var form = call.init.body;
        var dataPart = form.get("data");
        var textPromise = dataPart && typeof dataPart.text === "function" ? dataPart.text() : Promise.resolve(dataPart || "{}");
        var draftId = null;
        return textPromise.then(function (text) {
            var d = JSON.parse(text || "{}");
            return api(call, "POST", "/api/author/manuscripts", {
                title: d.title,
                genreId: d.genreId,
                synopsis: d.synopsis,
                language: d.language,
                wordCount: d.wordCount
            });
        }).then(function (draft) {
            draftId = draft.manuscriptId;
            var uploads = [["MANUSCRIPT", form.get("file")]];
            form.getAll("supportingFiles").forEach(function (f) {
                if (f && f.size) uploads.push(["SUPPORTING", f]);
            });
            return uploads.reduce(function (p, u) {
                return p.then(function () {
                    if (!u[1] || !u[1].size) return null;
                    var fd = new FormData();
                    fd.append("file", u[1], u[1].name);
                    fd.append("category", u[0]);
                    return api(call, "POST", "/api/author/manuscripts/" + draftId + "/files", fd);
                });
            }, Promise.resolve());
        }).then(function () {
            return api(call, "POST", "/api/author/manuscripts/" + draftId + "/submit");
        }).then(function (m) {
            return json(200, true, "Manuscript submitted successfully.", {
                manuscriptId: m.manuscriptId,
                manuscriptCode: m.manuscriptCode,
                title: m.title,
                status: m.status,
                submittedAt: m.submittedAt
            });
        }).catch(function (e) {
            if (draftId == null) throw e;
            return api(call, "DELETE", "/api/author/manuscripts/" + draftId).catch(function () {})
                .then(function () { throw e; });
        });
    }

    // ---------------------------------------------------------------- profile

    function profileView(p) {
        return Object.assign({}, p, {
            userId: currentUser().userId || null,
            fullName: fullName(p.firstName, p.lastName)
        });
    }

    function getProfile(call) {
        return api(call, "GET", "/api/author/profile").then(function (p) {
            return json(200, true, "Profile", profileView(p));
        });
    }

    function putProfile(call) {
        var changes = JSON.parse(call.init.body || "{}");
        return api(call, "GET", "/api/author/profile").then(function (p) {
            var body = {
                firstName: p.firstName,
                lastName: p.lastName,
                phoneNumber: changes.phoneNumber != null ? changes.phoneNumber : p.phoneNumber,
                penName: changes.penName,
                biography: changes.biography,
                dateOfBirth: changes.dateOfBirth || null,
                nationality: changes.nationality,
                website: changes.website
            };
            return api(call, "PUT", "/api/author/profile", body);
        }).then(function (p) {
            var msg = p.profileComplete
                ? "Profile updated."
                : "Profile saved. Add a biography, nationality and phone number before you submit a manuscript.";
            return json(200, true, msg, profileView(p));
        });
    }

    // ---------------------------------------------------------------- design approvals

    function pendingDesigns(call) {
        return api(call, "GET", "/api/author/designs").then(function (list) {
            return json(200, true, "Designs waiting for you", list.filter(function (x) { return x.design; }).map(function (x) {
                var d = x.design;
                return {
                    designId: d.designId,
                    manuscriptId: x.manuscript.manuscriptId,
                    manuscriptTitle: x.manuscript.title,
                    manuscriptCode: x.manuscript.manuscriptCode,
                    designVersion: d.designVersion,
                    designStatus: "AWAITING_YOUR_APPROVAL",
                    designNotes: d.designNotes,
                    submittedAt: d.submittedAt,
                    coverFileName: "cover-v" + d.designVersion,
                    layoutFileName: "layout-v" + d.designVersion,
                    manuscriptPdfFileName: "print-file-v" + d.designVersion + ".pdf"
                };
            }));
        });
    }

    function designHistory(call) {
        return api(call, "GET", "/api/author/manuscripts").then(function (list) {
            var withDesigns = list.filter(function (v) {
                return ACCEPTED_STAGES.indexOf(v.manuscript.status) > 0;
            });
            return Promise.all(withDesigns.map(function (v) {
                return api(call, "GET", "/api/author/manuscripts/" + v.manuscript.manuscriptId).catch(function () { return null; });
            }));
        }).then(function (details) {
            var rows = [];
            details.filter(Boolean).forEach(function (d) {
                (d.approvals || []).forEach(function (a) {
                    rows.push({
                        designId: a.designId,
                        manuscriptTitle: d.manuscript.title,
                        manuscriptCode: d.manuscript.manuscriptCode,
                        approvalStatus: a.approvalStatus,
                        comments: a.comments,
                        reviewedAt: a.reviewedAt
                    });
                });
            });
            rows.sort(function (a, b) { return String(b.reviewedAt || "").localeCompare(String(a.reviewedAt || "")); });
            return json(200, true, "Your design decisions", rows);
        });
    }

    function designDecision(call, m) {
        var b = JSON.parse(call.init.body || "{}");
        var approve = b.approve != null ? !!b.approve : String(b.decision || "").toUpperCase() === "APPROVE";
        return api(call, "POST", "/api/author/designs/" + m[1] + "/decision", { approve: approve, comments: b.comments })
            .then(function (d) {
                return json(200, true, approve ? "Design approved. It now goes to production." : "Design rejected. The designer will revise it.", d);
            });
    }

    function designDownload(call, m) {
        var kind = { cover: "cover", layout: "layout", "manuscript-pdf": "print", print: "print" }[m[2]];
        if (!kind) return json(404, false, "Unknown design file", null);
        return nativeFetch("/api/documents/designs/" + m[1] + "/" + kind, { headers: authOnly(call) });
    }

    /** The submit form shipped with fixed genre ids; fill it from the admin-managed genres instead. */
    function loadGenres() {
        var select = document.getElementById("genreId");
        if (!select || select.tagName !== "SELECT") return;
        nativeFetch("/api/genres").then(function (r) { return r.json(); }).then(function (res) {
            var genres = (res && res.data) || [];
            if (!genres.length) return;
            select.innerHTML = '<option value="">Select Genre</option>' + genres.map(function (g) {
                var name = String(g.genreName).replace(/&/g, "&amp;").replace(/</g, "&lt;");
                return '<option value="' + g.genreId + '">' + name + "</option>";
            }).join("");
        }).catch(function () {});
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", loadGenres);
    } else {
        loadGenres();
    }

    global.RpAuthorApi = { acceptedStages: ACCEPTED_STAGES };
})(window);

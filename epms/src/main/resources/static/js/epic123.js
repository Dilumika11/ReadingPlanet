/*
 * Epic 1-3 staff screens for the shared dashboard: editorial review (chief
 * editor and editors), design work (designers), design assignment and
 * quality control (production manager), order fulfilment (warehouse),
 * wholesale orders (sales) and people administration (admin).
 *
 * Like js/epic4.js, each screen registers in window.RPViews and uses the
 * helpers admin-dashboard.js exposes as window.RP. The API enforces every
 * rule; the screens show its message when it refuses something.
 */
(function () {
    "use strict";

    const V = window.RPViews = window.RPViews || {};

    // ---------- helpers ----------

    function R() { return window.RP; }
    function h(s) { return R().escapeHtml(s); }
    function money(v) { return R().money(v); }

    function badge(status) {
        const s = String(status || "");
        return '<span class="badge badge-' + s.toLowerCase() + '">' + h(s.replace(/_/g, " ")) + "</span>";
    }

    function when(v) {
        return v ? h(String(v).replace("T", " ").substring(0, 16)) : "-";
    }

    function loading(title) {
        R().pageTitle.textContent = title;
        R().content.innerHTML = '<div class="empty-state"><i class="fas fa-spinner fa-spin"></i> Loading...</div>';
    }

    function fail(err) {
        R().content.innerHTML = '<div class="alert alert-error">' + h(err.message) + "</div>";
    }

    function showMsg(id, type, msg) {
        const el = document.getElementById(id);
        if (el) el.innerHTML = msg ? '<div class="alert alert-' + type + '">' + h(msg) + "</div>" : "";
        if (el && msg) el.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }

    function card(title, inner, desc) {
        return '<div class="content-card">' + (title ? "<h2>" + title + "</h2>" : "") +
            (desc ? '<p class="card-desc">' + desc + "</p>" : "") + inner + "</div>";
    }

    function table(headers, rows, emptyText) {
        return '<div class="table-wrap"><table class="data-table"><thead><tr>' +
            headers.map(function (x) { return "<th>" + x + "</th>"; }).join("") +
            "</tr></thead><tbody>" +
            (rows || '<tr><td colspan="' + headers.length + '" class="empty-state">' + h(emptyText || "Nothing here yet.") + "</td></tr>") +
            "</tbody></table></div>";
    }

    function stat(label, value) {
        return '<div class="stat-card"><div class="stat-label">' + h(label) + '</div><div class="stat-value">' + value + "</div></div>";
    }

    function field(label, inputHtml, opts) {
        opts = opts || {};
        return '<div class="form-group' + (opts.full ? " full" : "") + '"><label>' + h(label) +
            (opts.required ? ' <span class="req">*</span>' : "") + "</label>" + inputHtml +
            (opts.hint ? '<div class="hint">' + h(opts.hint) + "</div>" : "") + "</div>";
    }

    function options(list, selected, placeholder) {
        let html = placeholder != null ? '<option value="">' + h(placeholder) + "</option>" : "";
        list.forEach(function (o) {
            html += '<option value="' + h(o.value) + '"' + (String(o.value) === String(selected) ? " selected" : "") + ">" + h(o.label) + "</option>";
        });
        return html;
    }

    function val(id) {
        const el = document.getElementById(id);
        return el ? el.value.trim() : "";
    }

    function btn(cls, label, attrs) {
        return '<button type="button" class="btn ' + cls + '" ' + (attrs || "") + ">" + label + "</button> ";
    }

    function on(selector, handler) {
        R().content.querySelectorAll(selector).forEach(function (el) {
            el.addEventListener("click", function () { handler(el.getAttribute("data-id"), el); });
        });
    }

    function post(url, body) {
        return R().api(url, { method: "POST", body: body == null ? undefined : JSON.stringify(body) });
    }

    async function act(msgId, fn, after) {
        try {
            const res = await fn();
            if (after) after(res);
        } catch (err) {
            showMsg(msgId, "error", err.message);
        }
    }

    function askReason(what) {
        const r = window.prompt("Reason to " + what + ":");
        if (r === null) return null;
        if (!r.trim()) { window.alert("A reason is required."); return null; }
        return r.trim();
    }

    async function download(url, name) {
        const res = await fetch(url, { headers: { "Authorization": "Bearer " + R().getToken() } });
        if (!res.ok) {
            const data = await res.json().catch(function () { return {}; });
            throw new Error(data.message || ("Download failed (" + res.status + ")"));
        }
        const blob = await res.blob();
        const m = /filename="?([^";]+)"?/.exec(res.headers.get("Content-Disposition") || "");
        const a = document.createElement("a");
        a.href = URL.createObjectURL(blob);
        a.download = m ? m[1] : name;
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(function () { URL.revokeObjectURL(a.href); }, 5000);
    }

    /** Buttons with data-url download a private document with the login token. */
    function bindDownloads(msgId) {
        on("[data-url]", function (_, el) {
            download(el.getAttribute("data-url"), el.getAttribute("data-name") || "document")
                .catch(function (e) { showMsg(msgId, "error", e.message); });
        });
    }

    function manuscriptRow(s, actions) {
        const m = s.manuscript;
        return "<tr><td>" + h(m.manuscriptCode) + "</td><td>" + h(m.title) + "</td><td>" + h(s.authorName) +
            "</td><td>" + h(s.genreName || "-") + "</td><td>" + badge(m.status) +
            (s.revisionOverdue ? ' <span class="badge badge-overdue">OVERDUE</span>' : "") +
            "</td><td>" + h(s.editorName || "-") + "</td><td>" + (s.daysInStatus == null ? "-" : s.daysInStatus) +
            "</td><td>" + actions + "</td></tr>";
    }

    const MS_HEAD = ["Code", "Title", "Author", "Genre", "Status", "Editor", "Days in status", "Actions"];

    /** Read-only manuscript sections shared by the editorial, design, production and admin screens. */
    function detailHtml(d, opts) {
        opts = opts || {};
        const m = d.manuscript;
        const people = d.people || {};
        const who = function (id) { return h(people[id] || (id ? "User #" + id : "-")); };
        let html = card(h(m.title) + " " + badge(m.status),
            '<div class="stats-grid">' +
            stat("Code", h(m.manuscriptCode)) + stat("Author", h(d.authorName)) + stat("Genre", h(d.genreName || "-")) +
            stat("Language", h(m.language || "-")) + stat("Words", m.wordCount == null ? "-" : Number(m.wordCount).toLocaleString()) +
            stat("Editor", h(d.editorName || "Not assigned")) + "</div>" +
            (m.synopsis ? '<p class="card-desc"><strong>Synopsis:</strong> ' + h(m.synopsis) + "</p>" : ""));

        if (opts.files !== false) {
            html += card("Manuscript files", table(["Version", "Type", "File", "Uploaded", "Notes", ""],
                (d.files || []).map(function (f) {
                    return "<tr><td>v" + f.fileVersion + "</td><td>" + badge(f.fileCategory) + "</td><td>" + h(f.fileName) +
                        "</td><td>" + when(f.uploadedAt) + "</td><td>" + h(f.notes || "") + "</td><td>" +
                        btn("btn-secondary", '<i class="fas fa-download"></i> Download',
                            'data-url="/api/documents/manuscript-files/' + f.manuscriptFileId + '" data-name="' + h(f.fileName) + '"') +
                        "</td></tr>";
                }).join(""), "No files uploaded."));
        }

        if (opts.reviews !== false && ((d.reviews || []).length || (d.revisions || []).length)) {
            html += card("Editorial reviews", table(["Round", "Reviewer", "Decision", "Comments", "When"],
                (d.reviews || []).map(function (r) {
                    return "<tr><td>" + r.reviewRound + "</td><td>" + who(r.reviewerId) + "</td><td>" + badge(r.decision) +
                        "</td><td>" + h(r.reviewComments) + "</td><td>" + when(r.reviewedAt) + "</td></tr>";
                }).join("")) +
                ((d.revisions || []).length ? "<h3>Revision requests</h3>" + table(["Round", "Requested changes", "Deadline", "Status", "Author's note"],
                    d.revisions.map(function (r) {
                        return "<tr><td>" + r.revisionRound + "</td><td>" + h(r.editorComments) + "</td><td>" + h(r.responseDeadline || "-") +
                            "</td><td>" + badge(r.status) + "</td><td>" + h(r.responseNotes || "") + "</td></tr>";
                    }).join("")) : ""));
        }

        if (opts.designs !== false && (d.designs || []).length) {
            html += card("Design versions", table(["Version", "Designer", "Status", "Notes", "Files", "Actions"],
                d.designs.map(function (x) {
                    const files = ["cover", "layout", "print"].filter(function (k) {
                        return x[k === "print" ? "printFilePath" : k + "FilePath"];
                    }).map(function (k) {
                        return btn("btn-secondary", k, 'data-url="/api/documents/designs/' + x.designId + "/" + k +
                            '" data-name="' + k + "-v" + x.designVersion + '"');
                    }).join("");
                    return "<tr><td>v" + x.designVersion + "</td><td>" + who(x.designerId) + "</td><td>" + badge(x.designStatus) +
                        "</td><td>" + h(x.designNotes || "") + "</td><td>" + (files || "-") + "</td><td>" +
                        (opts.designActions ? opts.designActions(x) : "") + "</td></tr>";
                }).join("")) +
                ((d.approvals || []).length ? "<h3>Author decisions</h3>" + table(["Design", "Decision", "Comments", "When"],
                    d.approvals.map(function (a) {
                        const dv = d.designs.filter(function (x) { return x.designId === a.designId; })[0];
                        return "<tr><td>v" + (dv ? dv.designVersion : "?") + "</td><td>" + badge(a.approvalStatus) + "</td><td>" +
                            h(a.comments || "") + "</td><td>" + when(a.reviewedAt) + "</td></tr>";
                    }).join("")) : ""));
        }

        if ((d.qualityChecks || []).length) {
            html += card("Quality checks", table(["When", "By", "Result", "Notes"],
                d.qualityChecks.map(function (q) {
                    return "<tr><td>" + when(q.checkedAt || q.createdAt) + "</td><td>" + who(q.checkedBy) + "</td><td>" +
                        badge(q.result) + "</td><td>" + h(q.notes || "") + "</td></tr>";
                }).join("")));
        }

        if (opts.history !== false) {
            html += card("Status history", table(["When", "From", "To", "By", "Note"],
                (d.history || []).slice().reverse().map(function (x) {
                    return "<tr><td>" + when(x.changedAt) + "</td><td>" + (x.fromStatus ? badge(x.fromStatus) : "-") + "</td><td>" +
                        badge(x.toStatus) + "</td><td>" + who(x.changedBy) + "</td><td>" + h(x.remarks || "") + "</td></tr>";
                }).join(""), "No history yet."));
        }
        return html;
    }

    function backBtn(view, label) {
        return '<p>' + btn("btn-secondary", '<i class="fas fa-arrow-left"></i> ' + h(label), 'id="backBtn"') + "</p>";
    }

    function bindBack(view) {
        const b = document.getElementById("backBtn");
        if (b) b.addEventListener("click", function () { R().navigate(view); });
    }

    // =====================================================================
    // EDITORIAL (US11 - US15)
    // =====================================================================

    const EDITORIAL_STATUSES = ["SUBMITTED", "UNDER_REVIEW", "REVISION_REQUESTED", "RESUBMITTED", "ACCEPTED", "REJECTED"];

    V["ed-overview"] = async function (status) {
        loading("Editorial Overview");
        try {
            const [counts, list, editors] = await Promise.all([
                R().api("/api/editorial/counts"),
                R().api("/api/editorial/overview" + (status ? "?status=" + encodeURIComponent(status) : "")),
                R().api("/api/editorial/editors")
            ]);
            const editorOpts = (editors.data || []).map(function (e) {
                return { value: e.userId, label: e.fullName + " (" + e.role.replace(/_/g, " ").toLowerCase() + ", " + e.activeManuscripts + " active)" };
            });
            const rows = (list.data || []).map(function (s) {
                const m = s.manuscript;
                const canAssign = ["SUBMITTED", "UNDER_REVIEW", "RESUBMITTED", "REVISION_REQUESTED"].indexOf(m.status) !== -1;
                return manuscriptRow(s,
                    (canAssign ? '<select id="ed-' + m.manuscriptId + '">' + options(editorOpts, m.assignedEditorId, "Choose editor") + "</select> " +
                        btn("btn-primary", "Assign", 'data-assign="' + m.manuscriptId + '" data-id="' + m.manuscriptId + '"') : "") +
                    btn("btn-secondary", "Open", 'data-open="' + m.manuscriptId + '" data-id="' + m.manuscriptId + '"'));
            }).join("");
            const c = counts.data || {};
            R().content.innerHTML =
                '<div class="stats-grid">' + EDITORIAL_STATUSES.map(function (s) { return stat(s.replace(/_/g, " "), c[s] || 0); }).join("") + "</div>" +
                '<div id="edMsg"></div>' +
                card("Manuscripts in editorial",
                    '<div class="form-grid">' + field("Status", '<select id="edFilter">' +
                        options(EDITORIAL_STATUSES.map(function (s) { return { value: s, label: s.replace(/_/g, " ") }; }), status, "All editorial stages") + "</select>") + "</div>" +
                    table(MS_HEAD, rows, "No manuscripts at this stage."),
                    "Assign each new submission to an editor and follow it through review.");
            document.getElementById("edFilter").addEventListener("change", function () { V["ed-overview"](this.value); });
            on("[data-assign]", function (id) {
                const editorId = val("ed-" + id);
                if (!editorId) { showMsg("edMsg", "error", "Choose an editor first."); return; }
                act("edMsg", function () {
                    return post("/api/editorial/manuscripts/" + id + "/assign?editorId=" + encodeURIComponent(editorId));
                }, function () { V["ed-overview"](status); });
            });
            on("[data-open]", function (id) { R().navigate("ed-manuscript", id); });
        } catch (e) { fail(e); }
    };

    V["ed-queue"] = async function () {
        loading("My Review Queue");
        try {
            const list = await R().api("/api/editorial/my-manuscripts");
            R().content.innerHTML = card("Manuscripts assigned to you",
                table(MS_HEAD, (list.data || []).map(function (s) {
                    return manuscriptRow(s, btn("btn-primary", "Review", 'data-id="' + s.manuscript.manuscriptId + '"'));
                }).join(""), "Nothing is assigned to you right now."),
                "Open a manuscript to read it and record your decision.");
            on("button[data-id]", function (id) { R().navigate("ed-manuscript", id); });
        } catch (e) { fail(e); }
    };

    V["ed-manuscript"] = async function (id) {
        loading("Manuscript Review");
        try {
            const d = (await R().api("/api/editorial/manuscripts/" + id)).data;
            const m = d.manuscript;
            const canDecide = m.status === "UNDER_REVIEW" || m.status === "RESUBMITTED";
            const back = R().user.roles.indexOf("CHIEF_EDITOR") !== -1 ? "ed-overview" : "ed-queue";
            R().content.innerHTML = backBtn(back, "Back") + '<div id="revMsg"></div>' +
                (canDecide ? card("Your decision",
                    '<div class="form-grid">' +
                    field("Decision", '<select id="decision">' + options([
                        { value: "ACCEPT", label: "Accept for production" },
                        { value: "REVISION_REQUIRED", label: "Request a revision" },
                        { value: "REJECT", label: "Reject" }], "", "Choose") + "</select>", { required: true }) +
                    field("Revision deadline", '<input type="date" id="deadline" min="' + R().todayISO() + '">', { hint: "Needed when you request a revision" }) +
                    field("Comments to the author", '<textarea id="comments" rows="4" maxlength="5000"></textarea>', { required: true, full: true }) +
                    '</div><div class="form-actions">' + btn("btn-primary", "Record decision", 'id="decideBtn"') + "</div>",
                    "The author sees your comments in their portal.") : "") +
                detailHtml(d);
            bindBack(back);
            bindDownloads("revMsg");
            const b = document.getElementById("decideBtn");
            if (b) b.addEventListener("click", function () {
                const decision = val("decision");
                const comments = val("comments");
                if (!decision || !comments) { showMsg("revMsg", "error", "Choose a decision and write your comments."); return; }
                if (decision === "REVISION_REQUIRED" && !val("deadline")) { showMsg("revMsg", "error", "Set a revision deadline."); return; }
                act("revMsg", function () {
                    return post("/api/editorial/manuscripts/" + id + "/decision",
                        { decision: decision, comments: comments, revisionDeadline: val("deadline") || null });
                }, function () { V["ed-manuscript"](id); });
            });
        } catch (e) { fail(e); }
    };

    // =====================================================================
    // DESIGN (US16 - US18)
    // =====================================================================

    V["ds-queue"] = async function () {
        loading("My Design Work");
        try {
            const list = await R().api("/api/design/queue");
            R().content.innerHTML = card("Books assigned to you",
                table(MS_HEAD, (list.data || []).map(function (s) {
                    return manuscriptRow(s, btn("btn-primary", "Open", 'data-id="' + s.manuscript.manuscriptId + '"'));
                }).join(""), "No books are assigned to you."),
                "Upload the cover, interior layout and print-ready PDF, then send the design to the author.");
            on("button[data-id]", function (id) { R().navigate("ds-manuscript", id); });
        } catch (e) { fail(e); }
    };

    V["ds-manuscript"] = async function (id) {
        loading("Book Design");
        try {
            const d = (await R().api("/api/design/manuscripts/" + id)).data;
            const m = d.manuscript;
            const canUpload = m.status === "ACCEPTED" || m.status === "IN_DESIGN";
            R().content.innerHTML = backBtn("ds-queue", "Back to my work") + '<div id="dsMsg"></div>' +
                (canUpload ? card("Upload a new design version",
                    '<div class="form-grid">' +
                    field("Cover (PNG or JPG)", '<input type="file" id="fCover" accept="image/png,image/jpeg">') +
                    field("Interior layout (PDF)", '<input type="file" id="fLayout" accept="application/pdf">') +
                    field("Print-ready file (PDF)", '<input type="file" id="fPrint" accept="application/pdf">') +
                    field("Notes for the author", '<textarea id="dNotes" rows="3" maxlength="2000"></textarea>', { full: true }) +
                    '</div><div class="form-actions">' + btn("btn-primary", "Upload version", 'id="upBtn"') + "</div>",
                    "Files you leave empty are carried over from the previous version.") : "") +
                detailHtml(d, {
                    reviews: false,
                    designActions: function (x) {
                        return x.designStatus === "DRAFT" && m.status === "IN_DESIGN"
                            ? btn("btn-primary", "Send to author", 'data-submit="' + x.designId + '" data-id="' + x.designId + '"') : "";
                    }
                });
            bindBack("ds-queue");
            bindDownloads("dsMsg");
            on("[data-submit]", function (designId) {
                act("dsMsg", function () { return post("/api/design/designs/" + designId + "/submit"); },
                    function () { V["ds-manuscript"](id); });
            });
            const up = document.getElementById("upBtn");
            if (up) up.addEventListener("click", function () {
                const fd = new FormData();
                [["fCover", "cover"], ["fLayout", "layout"], ["fPrint", "printFile"]].forEach(function (p) {
                    const f = document.getElementById(p[0]).files[0];
                    if (f) fd.append(p[1], f);
                });
                if (![].concat(fd.getAll("cover"), fd.getAll("layout"), fd.getAll("printFile")).length) {
                    showMsg("dsMsg", "error", "Choose at least one file.");
                    return;
                }
                if (val("dNotes")) fd.append("notes", val("dNotes"));
                act("dsMsg", function () { return R().apiUpload("/api/design/manuscripts/" + id + "/versions", fd); },
                    function () { V["ds-manuscript"](id); });
            });
        } catch (e) { fail(e); }
    };

    // =====================================================================
    // PRODUCTION MANAGER: designers, quality control, ready for printing (US16, US19, US20)
    // =====================================================================

    V["pm-design-assign"] = async function () {
        loading("Design Assignments");
        try {
            const [eligible, designers, active] = await Promise.all([
                R().api("/api/production/design-assignments/eligible"),
                R().api("/api/production/design-assignments/designers"),
                R().api("/api/production/design-assignments")
            ]);
            const opts = (designers.data || []).map(function (x) {
                return { value: x.userId, label: x.fullName + " (" + x.activeAssignments + " active)" };
            });
            R().content.innerHTML = '<div id="daMsg"></div>' +
                card("Accepted books waiting for a designer",
                    table(MS_HEAD, (eligible.data || []).map(function (s) {
                        const mid = s.manuscript.manuscriptId;
                        return manuscriptRow(s, '<select id="ds-' + mid + '">' + options(opts, "", "Choose designer") + "</select> " +
                            btn("btn-primary", "Assign", 'data-assign="' + mid + '" data-id="' + mid + '"'));
                    }).join(""), "No accepted manuscripts are waiting."),
                    "Editors accept a manuscript; you choose who designs it.") +
                card("Active assignments",
                    table(["Code", "Title", "Designer", "Book status", "Assigned", "Actions"], (active.data || []).map(function (r) {
                        return "<tr><td>" + h(r.manuscriptCode) + "</td><td>" + h(r.title) + "</td><td>" + h(r.designerName) +
                            "</td><td>" + badge(r.manuscriptStatus) + "</td><td>" + when(r.assignment.assignedAt) + "</td><td>" +
                            btn("btn-danger", "Cancel", 'data-cancel="' + r.assignment.assignmentId + '" data-id="' + r.assignment.assignmentId + '"') +
                            "</td></tr>";
                    }).join(""), "No active assignments."));
            on("[data-assign]", function (mid) {
                const designerId = val("ds-" + mid);
                if (!designerId) { showMsg("daMsg", "error", "Choose a designer first."); return; }
                act("daMsg", function () {
                    return post("/api/production/design-assignments", { manuscriptId: Number(mid), designerId: Number(designerId) });
                }, V["pm-design-assign"]);
            });
            on("[data-cancel]", function (aid) {
                if (!window.confirm("Cancel this design assignment?")) return;
                act("daMsg", function () { return R().api("/api/production/design-assignments/" + aid, { method: "DELETE" }); },
                    V["pm-design-assign"]);
            });
        } catch (e) { fail(e); }
    };

    V["pm-qc"] = async function () {
        loading("Quality Control");
        try {
            const list = await R().api("/api/production/queue");
            R().content.innerHTML = card("Approved designs",
                table(MS_HEAD, (list.data || []).map(function (s) {
                    const st = s.manuscript.status;
                    const label = st === "DESIGN_APPROVED" ? "Quality check" : st === "QC_PASSED" ? "Ready for printing" : "View";
                    return manuscriptRow(s, btn(st === "DESIGN_APPROVED" || st === "QC_PASSED" ? "btn-primary" : "btn-secondary",
                        label, 'data-id="' + s.manuscript.manuscriptId + '"'));
                }).join(""), "No designs are waiting for production."),
                "Check each author-approved design, then release the book for printing.");
            on("button[data-id]", function (id) { R().navigate("pm-manuscript", id); });
        } catch (e) { fail(e); }
    };

    V["pm-manuscript"] = async function (id) {
        loading("Production Check");
        try {
            const [detail, checklist, categories] = await Promise.all([
                R().api("/api/production/manuscripts/" + id),
                R().api("/api/production/checklist"),
                R().api("/api/categories")
            ]);
            const d = detail.data;
            const m = d.manuscript;
            const items = checklist.data || {};
            let form = "";
            if (m.status === "DESIGN_APPROVED") {
                form = card("Quality checklist",
                    Object.keys(items).map(function (k) {
                        return '<label class="checkbox-row" style="display:block;margin:6px 0"><input type="checkbox" data-check="' + h(k) + '"> ' + h(items[k]) + "</label>";
                    }).join("") +
                    '<div class="form-grid">' + field("Notes", '<textarea id="qcNotes" rows="3" maxlength="2000"></textarea>',
                        { full: true, hint: "Required when an item fails" }) + "</div>" +
                    '<div class="form-actions">' + btn("btn-primary", "Record quality check", 'id="qcBtn"') + "</div>",
                    "Every item must pass. A failed check goes back to the designer.");
            } else if (m.status === "QC_PASSED") {
                const catOpts = (categories.data || []).map(function (c) { return { value: c.categoryId, label: c.categoryName }; });
                form = card("Release for printing",
                    '<div class="form-grid">' +
                    field("ISBN", '<input id="rIsbn" maxlength="20" placeholder="978-955-...">', { required: true }) +
                    field("Price (Rs)", '<input id="rPrice" type="number" min="1" step="0.01">', { required: true }) +
                    field("Category", '<select id="rCat">' + options(catOpts, "", "Choose") + "</select>", { required: true }) +
                    field("Edition", '<input id="rEdition" maxlength="50" placeholder="1st">') +
                    field("Pages", '<input id="rPages" type="number" min="1">') +
                    field("Publication date", '<input id="rDate" type="date">') +
                    '</div><div class="form-actions">' + btn("btn-primary", "Mark ready for printing", 'id="rfpBtn"') + "</div>",
                    "This creates the book record so a print job can be scheduled.");
            }
            R().content.innerHTML = backBtn("pm-qc", "Back to quality control") + '<div id="pmMsg"></div>' + form +
                (d.book ? card("Book record", '<div class="stats-grid">' + stat("ISBN", h(d.book.isbn)) + stat("Price", money(d.book.price)) +
                    stat("Status", badge(d.book.bookStatus)) + "</div>") : "") +
                detailHtml(d, { reviews: false });
            bindBack("pm-qc");
            bindDownloads("pmMsg");
            const qc = document.getElementById("qcBtn");
            if (qc) qc.addEventListener("click", function () {
                const answers = {};
                R().content.querySelectorAll("[data-check]").forEach(function (c) { answers[c.getAttribute("data-check")] = c.checked; });
                const allPass = Object.keys(answers).every(function (k) { return answers[k]; });
                if (!allPass && !val("qcNotes")) { showMsg("pmMsg", "error", "Explain what failed in the notes."); return; }
                act("pmMsg", function () {
                    return post("/api/production/manuscripts/" + id + "/quality-check", { checklist: answers, notes: val("qcNotes") || null });
                }, function () { V["pm-manuscript"](id); });
            });
            const rfp = document.getElementById("rfpBtn");
            if (rfp) rfp.addEventListener("click", function () {
                if (!val("rIsbn") || !val("rPrice") || !val("rCat")) { showMsg("pmMsg", "error", "ISBN, price and category are required."); return; }
                act("pmMsg", function () {
                    return post("/api/production/manuscripts/" + id + "/ready-for-printing", {
                        isbn: val("rIsbn"), price: Number(val("rPrice")), categoryId: Number(val("rCat")),
                        edition: val("rEdition") || null, totalPages: val("rPages") ? Number(val("rPages")) : null,
                        publicationDate: val("rDate") || null
                    });
                }, function () { V["pm-manuscript"](id); });
            });
        } catch (e) { fail(e); }
    };

    // =====================================================================
    // WAREHOUSE: customer and wholesale fulfilment (US28, US30)
    // =====================================================================

    function dispatchForm(prefix, id) {
        return '<div id="' + prefix + id + '" style="display:none;margin-top:6px">' +
            '<input id="' + prefix + 'c' + id + '" placeholder="Courier" maxlength="100" style="width:120px"> ' +
            '<input id="' + prefix + 't' + id + '" placeholder="Tracking no." maxlength="100" style="width:120px"> ' +
            (prefix === "co" ? '<input id="' + prefix + 's' + id + '" type="number" min="0" placeholder="Cost" style="width:80px"> ' : "") +
            btn("btn-primary", "Confirm", 'data-confirm="' + id + '" data-id="' + id + '"') + "</div>";
    }

    const ORDER_STATUSES = ["CONFIRMED", "PACKED", "DISPATCHED", "DELIVERED", "CANCELLED"];

    V["wh-orders"] = async function (status) {
        loading("Customer Orders");
        try {
            const list = await R().api("/api/warehouse/orders" + (status ? "?status=" + status : ""));
            const rows = (list.data || []).map(function (v) {
                const o = v.order;
                const id = o.customerOrderId;
                let actions = "";
                if (o.orderStatus === "CONFIRMED") actions = btn("btn-primary", "Pack", 'data-pack="' + id + '" data-id="' + id + '"');
                if (o.orderStatus === "PACKED") actions = btn("btn-primary", "Dispatch", 'data-show="co' + id + '" data-id="' + id + '"') + dispatchForm("co", id);
                if (o.orderStatus === "DISPATCHED") actions = btn("btn-primary", "Mark delivered", 'data-deliver="' + id + '" data-id="' + id + '"');
                return "<tr><td>" + h(o.orderNumber) + "</td><td>" + when(o.orderDate) + "</td><td>" + h(o.recipientName) + "<br><small>" +
                    h(o.recipientPhone) + "</small></td><td>" + h(o.shippingAddress) + "</td><td>" +
                    (v.items || []).map(function (i) { return h(i.title) + " x " + i.quantity; }).join("<br>") + "</td><td>" +
                    money(o.totalAmount) + "</td><td>" + badge(o.orderStatus) +
                    (v.shipment && v.shipment.trackingNumber ? "<br><small>" + h(v.shipment.shippingProvider) + " " + h(v.shipment.trackingNumber) + "</small>" : "") +
                    "</td><td>" + actions + "</td></tr>";
            }).join("");
            R().content.innerHTML = '<div id="whMsg"></div>' + card("Online store orders",
                '<div class="form-grid">' + field("Status", '<select id="whFilter">' +
                    options(ORDER_STATUSES.map(function (s) { return { value: s, label: s }; }), status, "All orders") + "</select>") + "</div>" +
                table(["Order", "Placed", "Recipient", "Address", "Books", "Total", "Status", "Actions"], rows, "No orders."),
                "Pack confirmed orders, hand them to a courier, then confirm delivery. Delivered orders count as sales revenue.");
            document.getElementById("whFilter").addEventListener("change", function () { V["wh-orders"](this.value); });
            const again = function () { V["wh-orders"](status); };
            on("[data-pack]", function (id) { act("whMsg", function () { return post("/api/warehouse/orders/" + id + "/pack"); }, again); });
            on("[data-deliver]", function (id) { act("whMsg", function () { return post("/api/warehouse/orders/" + id + "/deliver"); }, again); });
            on("[data-show]", function (_, el) { document.getElementById(el.getAttribute("data-show")).style.display = "block"; el.style.display = "none"; });
            on("[data-confirm]", function (id) {
                if (!val("coc" + id)) { showMsg("whMsg", "error", "Enter the courier."); return; }
                act("whMsg", function () {
                    return post("/api/warehouse/orders/" + id + "/dispatch", {
                        shippingProvider: val("coc" + id), trackingNumber: val("cot" + id) || null,
                        shippingCost: val("cos" + id) ? Number(val("cos" + id)) : null
                    });
                }, again);
            });
        } catch (e) { fail(e); }
    };

    V["wh-wholesale"] = async function () {
        loading("Wholesale Dispatch");
        try {
            const list = await R().api("/api/warehouse/bookstore-orders");
            const rows = (list.data || []).map(function (v) {
                const o = v.order;
                const id = o.bookstoreOrderId;
                let actions = "";
                if (o.orderStatus === "APPROVED") actions = btn("btn-primary", "Dispatch", 'data-show="bo' + id + '" data-id="' + id + '"') + dispatchForm("bo", id);
                if (o.orderStatus === "DISPATCHED") actions = btn("btn-primary", "Mark delivered", 'data-deliver="' + id + '" data-id="' + id + '"');
                return "<tr><td>" + h(o.orderNumber) + "</td><td>" + h(v.bookstoreName) + "</td><td>" +
                    (v.items || []).map(function (i) { return h(i.title) + " x " + i.quantity; }).join("<br>") + "</td><td>" +
                    money(o.totalAmount) + "</td><td>" + badge(o.orderStatus) + "</td><td>" + actions + "</td></tr>";
            }).join("");
            R().content.innerHTML = '<div id="wwMsg"></div>' + card("Bookstore orders",
                table(["Order", "Bookstore", "Books", "Total", "Status", "Actions"], rows, "No wholesale orders."),
                "Only orders the sales team has approved can be dispatched.");
            on("[data-deliver]", function (id) {
                act("wwMsg", function () { return post("/api/warehouse/bookstore-orders/" + id + "/deliver"); }, V["wh-wholesale"]);
            });
            on("[data-show]", function (_, el) { document.getElementById(el.getAttribute("data-show")).style.display = "block"; el.style.display = "none"; });
            on("[data-confirm]", function (id) {
                if (!val("boc" + id)) { showMsg("wwMsg", "error", "Enter the courier or vehicle."); return; }
                act("wwMsg", function () {
                    return post("/api/warehouse/bookstore-orders/" + id + "/dispatch",
                        { shippingProvider: val("boc" + id), trackingNumber: val("bot" + id) || null });
                }, V["wh-wholesale"]);
            });
        } catch (e) { fail(e); }
    };

    // =====================================================================
    // SALES: bookstores and wholesale orders (US30)
    // =====================================================================

    V["sa-bookstores"] = async function (editId) {
        loading("Bookstores");
        try {
            const list = (await R().api("/api/sales/bookstores")).data || [];
            const e = list.filter(function (b) { return String(b.bookstoreId) === String(editId); })[0] || {};
            const f = function (label, id, key, opts) {
                return field(label, '<input id="' + id + '" value="' + h(e[key] || "") + '" maxlength="150">', opts);
            };
            R().content.innerHTML = '<div id="bsMsg"></div>' +
                card(e.bookstoreId ? "Edit " + h(e.bookstoreName) : "Add a bookstore",
                    '<div class="form-grid">' + f("Name", "bName", "bookstoreName", { required: true }) + f("Contact person", "bContact", "contactPerson") +
                    f("E-mail", "bEmail", "email") + f("Phone", "bPhone", "phoneNumber") + f("Address", "bAddress", "address") +
                    f("City", "bCity", "city") + f("Country", "bCountry", "country") + "</div>" +
                    '<div class="form-actions">' + btn("btn-primary", e.bookstoreId ? "Save" : "Add bookstore", 'id="bSave"') +
                    (e.bookstoreId ? btn("btn-secondary", "Cancel", 'id="bCancel"') : "") + "</div>") +
                card("Bookstores", table(["Name", "Contact", "E-mail", "Phone", "City", "Status", ""], list.map(function (b) {
                    return "<tr><td>" + h(b.bookstoreName) + "</td><td>" + h(b.contactPerson || "-") + "</td><td>" + h(b.email || "-") +
                        "</td><td>" + h(b.phoneNumber || "-") + "</td><td>" + h(b.city || "-") + "</td><td>" + badge(b.status) + "</td><td>" +
                        btn("btn-secondary", "Edit", 'data-id="' + b.bookstoreId + '"') + "</td></tr>";
                }).join(""), "No bookstores yet."));
            on("button[data-id]", function (id) { V["sa-bookstores"](id); });
            const c = document.getElementById("bCancel");
            if (c) c.addEventListener("click", function () { V["sa-bookstores"](); });
            document.getElementById("bSave").addEventListener("click", function () {
                if (!val("bName")) { showMsg("bsMsg", "error", "Enter the bookstore name."); return; }
                const body = {
                    bookstoreName: val("bName"), contactPerson: val("bContact") || null, email: val("bEmail") || null,
                    phoneNumber: val("bPhone") || null, address: val("bAddress") || null, city: val("bCity") || null, country: val("bCountry") || null
                };
                act("bsMsg", function () {
                    return e.bookstoreId
                        ? R().api("/api/sales/bookstores/" + e.bookstoreId, { method: "PUT", body: JSON.stringify(body) })
                        : post("/api/sales/bookstores", body);
                }, function () { V["sa-bookstores"](); });
            });
        } catch (err) { fail(err); }
    };

    V["sa-wholesale"] = async function () {
        loading("Wholesale Orders");
        try {
            const [stores, books, orders] = await Promise.all([
                R().api("/api/sales/bookstores"), R().api("/api/sales/books"), R().api("/api/sales/bookstore-orders")
            ]);
            const bookOpts = (books.data || []).map(function (b) {
                return { value: b.bookId, label: b.title + " (trade " + money(b.tradePrice) + ", " + b.available + " available)" };
            });
            const storeOpts = (stores.data || []).filter(function (s) { return s.status === "ACTIVE"; })
                .map(function (s) { return { value: s.bookstoreId, label: s.bookstoreName }; });
            const line = function () {
                return '<div class="form-grid wl-line">' + field("Book", '<select class="wl-book">' + options(bookOpts, "", "Choose") + "</select>") +
                    field("Quantity", '<input type="number" class="wl-qty" min="1" value="10">') + "</div>";
            };
            const rows = (orders.data || []).map(function (v) {
                const o = v.order;
                const pending = o.orderStatus === "PENDING_APPROVAL";
                return "<tr><td>" + h(o.orderNumber) + "</td><td>" + when(o.orderDate) + "</td><td>" + h(v.bookstoreName) + "</td><td>" +
                    (v.items || []).map(function (i) { return h(i.title) + " x " + i.quantity + " @ " + money(i.unitPrice); }).join("<br>") +
                    "</td><td>" + money(o.totalAmount) + "</td><td>" + badge(o.orderStatus) +
                    (o.rejectionReason ? "<br><small>" + h(o.rejectionReason) + "</small>" : "") + "</td><td>" + h(v.createdByName || "-") + "</td><td>" +
                    (pending ? btn("btn-primary", "Approve", 'data-approve="' + o.bookstoreOrderId + '" data-id="' + o.bookstoreOrderId + '"') +
                        btn("btn-danger", "Reject", 'data-reject="' + o.bookstoreOrderId + '" data-id="' + o.bookstoreOrderId + '"') : "") + "</td></tr>";
            }).join("");
            R().content.innerHTML = '<div id="woMsg"></div>' +
                card("New wholesale order",
                    '<div class="form-grid">' + field("Bookstore", '<select id="woStore">' + options(storeOpts, "", "Choose") + "</select>", { required: true }) +
                    "</div>" + '<div id="woLines">' + line() + "</div>" +
                    '<div class="form-actions">' + btn("btn-secondary", "Add another book", 'id="woAdd"') +
                    btn("btn-primary", "Create order", 'id="woCreate"') + "</div>",
                    "Bookstores pay the trade price (75% of list). An order waits for approval before stock is reserved.") +
                card("Orders", table(["Order", "Date", "Bookstore", "Books", "Total", "Status", "Created by", "Actions"], rows, "No wholesale orders yet."));
            document.getElementById("woAdd").addEventListener("click", function () {
                document.getElementById("woLines").insertAdjacentHTML("beforeend", line());
            });
            document.getElementById("woCreate").addEventListener("click", function () {
                const items = [];
                R().content.querySelectorAll(".wl-line").forEach(function (l) {
                    const b = l.querySelector(".wl-book").value;
                    const q = Number(l.querySelector(".wl-qty").value);
                    if (b && q > 0) items.push({ bookId: Number(b), quantity: q });
                });
                if (!val("woStore") || !items.length) { showMsg("woMsg", "error", "Choose a bookstore and at least one book."); return; }
                act("woMsg", function () {
                    return post("/api/sales/bookstore-orders", { bookstoreId: Number(val("woStore")), items: items });
                }, V["sa-wholesale"]);
            });
            on("[data-approve]", function (id) {
                act("woMsg", function () { return post("/api/sales/bookstore-orders/" + id + "/approve"); }, V["sa-wholesale"]);
            });
            on("[data-reject]", function (id) {
                const reason = askReason("reject this order");
                if (!reason) return;
                act("woMsg", function () { return post("/api/sales/bookstore-orders/" + id + "/reject", { reason: reason }); }, V["sa-wholesale"]);
            });
        } catch (e) { fail(e); }
    };

    // =====================================================================
    // ADMIN: applications, staff accounts, authors, manuscripts (US8 - US10)
    // =====================================================================

    V["ad-applications"] = async function () {
        loading("Publishing Applications");
        try {
            const list = (await R().api("/api/publishing-applications/admin")).data || [];
            R().content.innerHTML = '<div id="apMsg"></div>' + card("Getting Published applications",
                table(["#", "Applicant", "Contact", "Manuscript", "Type", "Submitted", "Status", "Actions"], list.map(function (a) {
                    const pending = a.status === "PENDING";
                    return "<tr><td>" + a.applicationId + "</td><td>" + h(a.authorName) + "</td><td>" + h(a.email) + "<br><small>" + h(a.phone) +
                        "</small></td><td>" + h(a.manuscriptName) + (a.shortDescription ? "<br><small>" + h(a.shortDescription) + "</small>" : "") +
                        "</td><td>" + h(a.bookType) + "</td><td>" + when(a.submittedAt) + "</td><td>" + badge(a.status) +
                        (a.rejectionReason ? "<br><small>" + h(a.rejectionReason) + "</small>" : "") + "</td><td>" +
                        (a.fileName ? btn("btn-secondary", '<i class="fas fa-download"></i>',
                            'data-url="/api/publishing-applications/admin/' + a.applicationId + '/file" data-name="' + h(a.fileName) + '" title="Download manuscript"') : "") +
                        (pending ? btn("btn-primary", "Approve", 'data-approve="' + a.applicationId + '" data-id="' + a.applicationId + '"') +
                            btn("btn-danger", "Reject", 'data-reject="' + a.applicationId + '" data-id="' + a.applicationId + '"') : "") +
                        "</td></tr>";
                }).join(""), "No applications yet."),
                "Approved applicants can register as authors with the same e-mail address.");
            bindDownloads("apMsg");
            on("[data-approve]", function (id) {
                act("apMsg", function () { return post("/api/publishing-applications/" + id + "/approve"); }, V["ad-applications"]);
            });
            on("[data-reject]", function (id) {
                const reason = askReason("reject this application");
                if (!reason) return;
                act("apMsg", function () { return post("/api/publishing-applications/" + id + "/reject", { reason: reason }); }, V["ad-applications"]);
            });
        } catch (e) { fail(e); }
    };

    const STAFF_ROLES = ["ADMIN", "CHIEF_EDITOR", "EDITOR", "DESIGNER", "PRODUCTION_MANAGER", "INVENTORY_STAFF",
        "SALES_STAFF", "FINANCE_STAFF", "EXECUTIVE"];

    V["ad-users"] = async function () {
        loading("User Accounts");
        try {
            const list = (await R().api("/api/admin/users")).data || [];
            R().content.innerHTML = '<div id="usMsg"></div>' +
                card("Create a staff account",
                    '<div class="form-grid">' +
                    field("First name", '<input id="uFirst" maxlength="100">', { required: true }) +
                    field("Last name", '<input id="uLast" maxlength="100">', { required: true }) +
                    field("Username", '<input id="uUser" maxlength="50">', { required: true }) +
                    field("E-mail", '<input id="uEmail" type="email" maxlength="150">', { required: true }) +
                    field("Temporary password", '<input id="uPass" type="password" maxlength="100">', { required: true, hint: "At least 8 characters" }) +
                    field("Role", '<select id="uRole">' + options(STAFF_ROLES.map(function (r) { return { value: r, label: r.replace(/_/g, " ") }; }), "", "Choose") + "</select>", { required: true }) +
                    '</div><div class="form-actions">' + btn("btn-primary", "Create account", 'id="uCreate"') + "</div>",
                    "Authors and customers sign up themselves; staff accounts are created here.") +
                card("All accounts", table(["Name", "Username", "E-mail", "Role", "Status", "Created", ""], list.map(function (u) {
                    const active = u.accountStatus === "ACTIVE";
                    const self = u.userId === R().user.userId;
                    return "<tr><td>" + h(u.fullName) + "</td><td>" + h(u.username) + "</td><td>" + h(u.email) + "</td><td>" +
                        badge(u.role) + "</td><td>" + badge(u.accountStatus) + "</td><td>" + when(u.createdAt) + "</td><td>" +
                        (self ? "" : btn(active ? "btn-danger" : "btn-secondary", active ? "Deactivate" : "Activate",
                            'data-status="' + (active ? "INACTIVE" : "ACTIVE") + '" data-id="' + u.userId + '"')) + "</td></tr>";
                }).join("")));
            on("[data-status]", function (id, el) {
                act("usMsg", function () {
                    return post("/api/admin/users/" + id + "/status?status=" + el.getAttribute("data-status"));
                }, V["ad-users"]);
            });
            document.getElementById("uCreate").addEventListener("click", function () {
                const body = {
                    firstName: val("uFirst"), lastName: val("uLast"), username: val("uUser"), email: val("uEmail"),
                    password: document.getElementById("uPass").value, role: val("uRole")
                };
                if (!body.firstName || !body.lastName || !body.username || !body.email || !body.password || !body.role) {
                    showMsg("usMsg", "error", "Fill in every field.");
                    return;
                }
                act("usMsg", async function () {
                    const res = await post("/api/admin/users", body);
                    if (res && res.success === false) throw new Error(res.message);
                    return res;
                }, V["ad-users"]);
            });
        } catch (e) { fail(e); }
    };

    V["ad-authors"] = async function () {
        loading("Authors");
        try {
            const list = (await R().api("/api/admin/authors")).data || [];
            R().content.innerHTML = card("Authors", table(["Code", "Name", "E-mail", "Phone", "Nationality", "Account", "Manuscripts"],
                list.map(function (r) {
                    return "<tr><td>" + h(r.author.authorCode) + "</td><td>" + h(r.name) + "</td><td>" + h(r.email || "-") + "</td><td>" +
                        h(r.phoneNumber || "-") + "</td><td>" + h(r.author.nationality || "-") + "</td><td>" + badge(r.accountStatus) +
                        "</td><td>" + r.manuscripts + "</td></tr>";
                }).join(""), "No authors yet."));
        } catch (e) { fail(e); }
    };

    V["ad-manuscripts"] = async function () {
        loading("Manuscripts");
        try {
            const list = (await R().api("/api/admin/manuscripts")).data || [];
            R().content.innerHTML = card("Submitted manuscripts",
                table(MS_HEAD, list.map(function (s) {
                    return manuscriptRow(s, btn("btn-secondary", "Open", 'data-id="' + s.manuscript.manuscriptId + '"'));
                }).join(""), "No manuscripts have been submitted."),
                "Every manuscript from submission to publication.");
            on("button[data-id]", function (id) { R().navigate("ad-manuscript", id); });
        } catch (e) { fail(e); }
    };

    V["ad-manuscript"] = async function (id) {
        loading("Manuscript");
        try {
            const d = (await R().api("/api/admin/manuscripts/" + id)).data;
            R().content.innerHTML = backBtn("ad-manuscripts", "Back to manuscripts") + '<div id="amMsg"></div>' + detailHtml(d);
            bindBack("ad-manuscripts");
            bindDownloads("amMsg");
        } catch (e) { fail(e); }
    };
})();

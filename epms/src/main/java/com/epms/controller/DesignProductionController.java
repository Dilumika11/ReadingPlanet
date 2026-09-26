package com.epms.controller;

import com.epms.dto.request.QualityCheckRequest;
import com.epms.dto.request.ReadyForPrintingRequest;
import com.epms.dto.response.ApiResponse;
import com.epms.security.service.CurrentUserService;
import com.epms.service.DesignProductionService;
import com.epms.service.EditorialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** Epic 2 (US16 - US20): designer, author approval and production manager endpoints. */
@RestController
@RequiredArgsConstructor
public class DesignProductionController {

    private final DesignProductionService service;
    private final EditorialService editorialService;
    private final CurrentUserService currentUserService;

    // ---------- designer (US16, US17) ----------

    @GetMapping("/api/design/queue")
    public ApiResponse<?> designQueue(Authentication auth) {
        return new ApiResponse<>(true, "Your design assignments", service.designQueue(currentUserService.getCurrentUserId(auth)));
    }

    // ---------- design assignments (production manager) ----------

    @GetMapping("/api/production/design-assignments/eligible")
    public ApiResponse<?> awaitingDesigner() {
        return new ApiResponse<>(true, "Manuscripts waiting for a designer", service.awaitingDesigner());
    }

    @GetMapping("/api/production/design-assignments/designers")
    public ApiResponse<?> designers() {
        return new ApiResponse<>(true, "Designers", service.designers());
    }

    @GetMapping("/api/production/design-assignments")
    public ApiResponse<?> activeAssignments() {
        return new ApiResponse<>(true, "Active design assignments", service.activeAssignments());
    }

    @PostMapping("/api/production/design-assignments")
    public ApiResponse<?> assignDesigner(@RequestBody Map<String, Object> body, Authentication auth) {
        Object m = body.get("manuscriptId"), d = body.get("designerId"), r = body.get("remarks");
        if (m == null || d == null) {
            throw new com.epms.exception.InvalidRequestException("Choose the manuscript and the designer");
        }
        return new ApiResponse<>(true, "Designer assigned", service.assignDesigner(currentUserService.getCurrentUserId(auth),
                Long.valueOf(m.toString()), Long.valueOf(d.toString()), r == null ? null : r.toString()));
    }

    @DeleteMapping("/api/production/design-assignments/{id}")
    public ApiResponse<?> cancelAssignment(@PathVariable Long id, Authentication auth) {
        service.cancelAssignment(currentUserService.getCurrentUserId(auth), id);
        return new ApiResponse<>(true, "Assignment cancelled", null);
    }

    @GetMapping("/api/design/manuscripts/{id}")
    public ApiResponse<?> designDetail(@PathVariable Long id, Authentication auth) {
        return new ApiResponse<>(true, "Manuscript", editorialService.detail(currentUserService.getCurrentUser(auth), id));
    }

    @PostMapping(value = "/api/design/manuscripts/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> upload(@PathVariable Long id,
                                 @RequestParam(value = "cover", required = false) MultipartFile cover,
                                 @RequestParam(value = "layout", required = false) MultipartFile layout,
                                 @RequestParam(value = "printFile", required = false) MultipartFile printFile,
                                 @RequestParam(value = "notes", required = false) String notes, Authentication auth) {
        return new ApiResponse<>(true, "Design version uploaded",
                service.uploadVersion(currentUserService.getCurrentUserId(auth), id, cover, layout, printFile, notes));
    }

    @PostMapping("/api/design/designs/{designId}/submit")
    public ApiResponse<?> submit(@PathVariable Long designId, Authentication auth) {
        return new ApiResponse<>(true, "Sent to the author for approval",
                service.submitForApproval(currentUserService.getCurrentUserId(auth), designId));
    }

    // ---------- author (US18) ----------

    @GetMapping("/api/author/designs")
    public ApiResponse<?> pending(Authentication auth) {
        return new ApiResponse<>(true, "Designs waiting for you", service.pendingForAuthor(currentUserService.getCurrentUserId(auth)));
    }

    @PostMapping("/api/author/designs/{designId}/decision")
    public ApiResponse<?> decide(@PathVariable Long designId, @RequestBody Map<String, Object> body, Authentication auth) {
        boolean approve = Boolean.TRUE.equals(body.get("approve"));
        Object comments = body.get("comments");
        return new ApiResponse<>(true, approve ? "Design approved" : "Design rejected",
                service.authorDecision(currentUserService.getCurrentUserId(auth), designId, approve,
                        comments == null ? null : comments.toString()));
    }

    // ---------- production manager (US19, US20) ----------

    @GetMapping("/api/production/queue")
    public ApiResponse<?> productionQueue() {
        return new ApiResponse<>(true, "Production queue", service.productionQueue());
    }

    @GetMapping("/api/production/checklist")
    public ApiResponse<?> checklist() {
        return new ApiResponse<>(true, "Quality checklist", DesignProductionService.CHECKLIST);
    }

    @GetMapping("/api/production/manuscripts/{id}")
    public ApiResponse<?> productionDetail(@PathVariable Long id, Authentication auth) {
        return new ApiResponse<>(true, "Manuscript", editorialService.detail(currentUserService.getCurrentUser(auth), id));
    }

    @PostMapping("/api/production/manuscripts/{id}/quality-check")
    public ApiResponse<?> qualityCheck(@PathVariable Long id, @Valid @RequestBody QualityCheckRequest request, Authentication auth) {
        return new ApiResponse<>(true, "Quality check recorded",
                service.qualityCheck(currentUserService.getCurrentUserId(auth), id, request));
    }

    @PostMapping("/api/production/manuscripts/{id}/ready-for-printing")
    public ApiResponse<?> ready(@PathVariable Long id, @Valid @RequestBody ReadyForPrintingRequest request, Authentication auth) {
        return new ApiResponse<>(true, "Book is ready for printing",
                service.markReadyForPrinting(currentUserService.getCurrentUserId(auth), id, request));
    }
}

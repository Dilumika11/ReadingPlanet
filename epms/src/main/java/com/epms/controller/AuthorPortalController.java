package com.epms.controller;

import com.epms.dto.request.AuthorProfileRequest;
import com.epms.dto.request.ManuscriptRequest;
import com.epms.dto.response.ApiResponse;
import com.epms.security.service.CurrentUserService;
import com.epms.service.AuthorPortalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Epic 1 (US1 - US7): an author's own profile and manuscripts. AUTHOR role only. */
@RestController
@RequestMapping("/api/author")
@RequiredArgsConstructor
public class AuthorPortalController {

    private final AuthorPortalService service;
    private final CurrentUserService currentUserService;

    @GetMapping("/profile")
    public ApiResponse<?> profile(Authentication auth) {
        return new ApiResponse<>(true, "Profile", service.getProfile(uid(auth)));
    }

    @PutMapping("/profile")
    public ApiResponse<?> updateProfile(@Valid @RequestBody AuthorProfileRequest request, Authentication auth) {
        return new ApiResponse<>(true, "Profile saved", service.updateProfile(uid(auth), request));
    }

    @GetMapping("/manuscripts")
    public ApiResponse<?> manuscripts(Authentication auth) {
        return new ApiResponse<>(true, "Your manuscripts", service.myManuscripts(uid(auth)));
    }

    @GetMapping("/manuscripts/{id}")
    public ApiResponse<?> manuscript(@PathVariable Long id, Authentication auth) {
        return new ApiResponse<>(true, "Manuscript", service.myManuscript(uid(auth), id));
    }

    @PostMapping("/manuscripts")
    public ApiResponse<?> create(@Valid @RequestBody ManuscriptRequest request, Authentication auth) {
        return new ApiResponse<>(true, "Draft created", service.create(uid(auth), request));
    }

    @PutMapping("/manuscripts/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody ManuscriptRequest request, Authentication auth) {
        return new ApiResponse<>(true, "Draft updated", service.update(uid(auth), id, request));
    }

    @DeleteMapping("/manuscripts/{id}")
    public ApiResponse<?> delete(@PathVariable Long id, Authentication auth) {
        service.deleteDraft(uid(auth), id);
        return new ApiResponse<>(true, "Draft deleted", null);
    }

    @PostMapping(value = "/manuscripts/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "category", required = false) String category,
                                 @RequestParam(value = "notes", required = false) String notes, Authentication auth) {
        return new ApiResponse<>(true, "File uploaded", service.uploadFile(uid(auth), id, file, category, notes));
    }

    @PostMapping("/manuscripts/{id}/submit")
    public ApiResponse<?> submit(@PathVariable Long id, Authentication auth) {
        return new ApiResponse<>(true, "Manuscript submitted", service.submit(uid(auth), id));
    }

    @PostMapping(value = "/manuscripts/{id}/revision", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> revision(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "notes", required = false) String notes, Authentication auth) {
        return new ApiResponse<>(true, "Revised manuscript uploaded", service.respondToRevision(uid(auth), id, file, notes));
    }

    private Long uid(Authentication auth) {
        return currentUserService.getCurrentUserId(auth);
    }
}

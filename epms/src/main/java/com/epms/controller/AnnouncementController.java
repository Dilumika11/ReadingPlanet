package com.epms.controller;

import com.epms.dto.request.AnnouncementRequest;
import com.epms.dto.response.ApiResponse;
import com.epms.security.service.CurrentUserService;
import com.epms.service.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ApiResponse<?> getAll() {
        return new ApiResponse<>(true, "Announcements retrieved", announcementService.getAll());
    }

    /** Announcements currently visible to the logged-in user's role. */
    @GetMapping("/active")
    public ApiResponse<?> getActive(Authentication authentication) {
        return new ApiResponse<>(true, "Active announcements retrieved",
                announcementService.getActiveFor(currentUserService.getCurrentUser(authentication).getRole()));
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody AnnouncementRequest request, Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Announcement created", announcementService.create(request, userId));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody AnnouncementRequest request) {
        return new ApiResponse<>(true, "Announcement updated", announcementService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return new ApiResponse<>(true, "Announcement deleted", null);
    }
}

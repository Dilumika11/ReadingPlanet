package com.epms.controller;

import com.epms.dto.request.SettingUpdateRequest;
import com.epms.dto.response.ApiResponse;
import com.epms.security.service.CurrentUserService;
import com.epms.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ApiResponse<?> getAll() {
        return new ApiResponse<>(true, "Settings retrieved", settingsService.getAll());
    }

    /** Readable without login: currency, tax rate, invoice prefix and company details for other epics. */
    @GetMapping("/public")
    public ApiResponse<?> getPublic() {
        return new ApiResponse<>(true, "Public settings retrieved", settingsService.getPublicSettings());
    }

    /** Who changed which setting, when, from what to what. */
    @GetMapping("/history")
    public ApiResponse<?> getHistory() {
        return new ApiResponse<>(true, "Settings history retrieved", settingsService.getHistory());
    }

    @PutMapping
    public ApiResponse<?> update(@Valid @RequestBody SettingUpdateRequest request, Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Setting updated", settingsService.upsert(request, userId));
    }
}

package com.epms.controller;

import com.epms.dto.request.RoyaltyPayRequest;
import com.epms.dto.response.ApiResponse;
import com.epms.security.service.CurrentUserService;
import com.epms.service.RoyaltyPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/royalty-payments")
@RequiredArgsConstructor
public class RoyaltyPaymentController {

    private final RoyaltyPaymentService royaltyPaymentService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ApiResponse<?> getAll() {
        return new ApiResponse<>(true, "Royalty payments retrieved", royaltyPaymentService.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<?> getById(@PathVariable Long id) {
        return new ApiResponse<>(true, "Royalty payment retrieved", royaltyPaymentService.getById(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<?> approve(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Royalty payment approved", royaltyPaymentService.approve(id, userId));
    }

    @PostMapping("/{id}/schedule")
    public ApiResponse<?> schedule(@PathVariable Long id) {
        return new ApiResponse<>(true, "Royalty payment scheduled", royaltyPaymentService.schedule(id));
    }

    @PostMapping("/{id}/process")
    public ApiResponse<?> process(@PathVariable Long id) {
        return new ApiResponse<>(true, "Royalty payment processing", royaltyPaymentService.process(id));
    }

    @PostMapping("/{id}/mark-paid")
    public ApiResponse<?> markPaid(@PathVariable Long id, @Valid @RequestBody RoyaltyPayRequest request,
                                   Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Royalty payment marked paid", royaltyPaymentService.markPaid(id, request, userId));
    }
}

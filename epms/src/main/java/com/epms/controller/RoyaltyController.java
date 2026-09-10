package com.epms.controller;

import com.epms.dto.request.RoyaltyAgreementRequest;
import com.epms.dto.request.RoyaltyCalculationRequest;
import com.epms.dto.response.ApiResponse;
import com.epms.security.service.CurrentUserService;
import com.epms.service.RoyaltyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class RoyaltyController {

    private final RoyaltyService royaltyService;
    private final CurrentUserService currentUserService;

    // --- Agreements ---

    @GetMapping("/api/royalty-agreements")
    public ApiResponse<?> getAllAgreements() {
        return new ApiResponse<>(true, "Royalty agreements retrieved", royaltyService.getAllAgreements());
    }

    @GetMapping("/api/royalty-agreements/{id}")
    public ApiResponse<?> getAgreementById(@PathVariable Long id) {
        return new ApiResponse<>(true, "Royalty agreement retrieved", royaltyService.getAgreementById(id));
    }

    @GetMapping("/api/royalty-agreements/author/{authorId}")
    public ApiResponse<?> getAgreementsByAuthor(@PathVariable Long authorId) {
        return new ApiResponse<>(true, "Royalty agreements retrieved", royaltyService.getAgreementsByAuthor(authorId));
    }

    @PostMapping("/api/royalty-agreements")
    public ApiResponse<?> createAgreement(@Valid @RequestBody RoyaltyAgreementRequest request) {
        return new ApiResponse<>(true, "Royalty agreement created (DRAFT)", royaltyService.createAgreement(request));
    }

    @PostMapping("/api/royalty-agreements/{id}/activate")
    public ApiResponse<?> activateAgreement(@PathVariable Long id) {
        return new ApiResponse<>(true, "Royalty agreement activated", royaltyService.activateAgreement(id));
    }

    @PostMapping("/api/royalty-agreements/{id}/expire")
    public ApiResponse<?> expireAgreement(@PathVariable Long id) {
        return new ApiResponse<>(true, "Royalty agreement expired", royaltyService.expireAgreement(id));
    }

    // --- Calculations ---

    @GetMapping("/api/royalties")
    public ApiResponse<?> getAll() {
        return new ApiResponse<>(true, "Royalty calculations retrieved", royaltyService.getAll());
    }

    @GetMapping("/api/royalties/{id}")
    public ApiResponse<?> getById(@PathVariable Long id) {
        return new ApiResponse<>(true, "Royalty calculation retrieved", royaltyService.getById(id));
    }

    @GetMapping("/api/royalties/author/{authorId}")
    public ApiResponse<?> getByAuthor(@PathVariable Long authorId) {
        return new ApiResponse<>(true, "Royalty calculations retrieved", royaltyService.getByAuthor(authorId));
    }

    @PostMapping("/api/royalties/calculate")
    public ApiResponse<?> calculate(@Valid @RequestBody RoyaltyCalculationRequest request, Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Royalty calculated", royaltyService.calculate(request, userId));
    }

    @PostMapping("/api/royalties/{id}/recalculate")
    public ApiResponse<?> recalculate(@PathVariable Long id) {
        return new ApiResponse<>(true, "Royalty recalculated", royaltyService.recalculate(id));
    }

    @GetMapping("/api/royalties/{id}/statement")
    public ApiResponse<?> getStatement(@PathVariable Long id) {
        // A statement is a read-only presentation of a finalized calculation.
        return new ApiResponse<>(true, "Royalty statement retrieved", royaltyService.getById(id));
    }
}

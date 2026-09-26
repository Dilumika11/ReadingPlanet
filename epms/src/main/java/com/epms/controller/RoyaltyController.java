package com.epms.controller;

import com.epms.dto.request.ReasonRequest;
import com.epms.dto.request.RoyaltyAgreementRequest;
import com.epms.dto.request.RoyaltyCalculationRequest;
import com.epms.dto.request.RoyaltyPayRequest;
import com.epms.dto.request.RoyaltyRejectRequest;
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

    // --- Agreements (US41) ---

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

    /** Authors from the Epic 1 author directory, for agreement forms. */
    @GetMapping("/api/royalty-agreements/lookup/authors")
    public ApiResponse<?> lookupAuthors() {
        return new ApiResponse<>(true, "Authors retrieved", royaltyService.getAuthors());
    }

    /** Books from the catalogue, optionally only one author's. */
    @GetMapping("/api/royalty-agreements/lookup/books")
    public ApiResponse<?> lookupBooks(@RequestParam(required = false) Long authorId) {
        return new ApiResponse<>(true, "Books retrieved", royaltyService.getBooks(authorId));
    }

    @PostMapping("/api/royalty-agreements")
    public ApiResponse<?> createAgreement(@Valid @RequestBody RoyaltyAgreementRequest request) {
        return new ApiResponse<>(true, "Royalty agreement created (DRAFT)", royaltyService.createAgreement(request));
    }

    @PutMapping("/api/royalty-agreements/{id}")
    public ApiResponse<?> updateAgreement(@PathVariable Long id, @Valid @RequestBody RoyaltyAgreementRequest request) {
        return new ApiResponse<>(true, "Royalty agreement updated", royaltyService.updateAgreement(id, request));
    }

    @PostMapping("/api/royalty-agreements/{id}/activate")
    public ApiResponse<?> activateAgreement(@PathVariable Long id) {
        return new ApiResponse<>(true, "Royalty agreement activated", royaltyService.activateAgreement(id));
    }

    @PostMapping("/api/royalty-agreements/{id}/expire")
    public ApiResponse<?> expireAgreement(@PathVariable Long id) {
        return new ApiResponse<>(true, "Royalty agreement expired", royaltyService.expireAgreement(id));
    }

    // --- Calculations (US42, US43) ---

    @GetMapping("/api/royalties")
    public ApiResponse<?> getAll(@RequestParam(required = false) String status) {
        return new ApiResponse<>(true, "Royalty calculations retrieved", royaltyService.getAll(status));
    }

    @GetMapping("/api/royalties/{id}")
    public ApiResponse<?> getById(@PathVariable Long id) {
        return new ApiResponse<>(true, "Royalty calculation retrieved", royaltyService.getDetail(id));
    }

    @GetMapping("/api/royalties/author/{authorId}")
    public ApiResponse<?> getByAuthor(@PathVariable Long authorId) {
        return new ApiResponse<>(true, "Royalty calculations retrieved", royaltyService.getByAuthor(authorId));
    }

    /** Runs the calculation and shows the result without saving it. */
    @PostMapping("/api/royalties/preview")
    public ApiResponse<?> preview(@Valid @RequestBody RoyaltyCalculationRequest request) {
        return new ApiResponse<>(true, "Royalty preview", royaltyService.preview(request));
    }

    @PostMapping("/api/royalties/calculate")
    public ApiResponse<?> calculate(@Valid @RequestBody RoyaltyCalculationRequest request, Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Royalty calculated", royaltyService.calculate(request, userId));
    }

    @PostMapping("/api/royalties/{id}/recalculate")
    public ApiResponse<?> recalculate(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Royalty recalculated", royaltyService.recalculate(id, userId));
    }

    @PostMapping("/api/royalties/{id}/cancel")
    public ApiResponse<?> cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest request,
                                 Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Royalty calculation cancelled", royaltyService.cancel(id, request.getReason(), userId));
    }

    // --- Statements, approval, payment (US44 - US46) ---

    @PostMapping("/api/royalties/{id}/statement")
    public ApiResponse<?> issueStatement(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Royalty statement issued", royaltyService.issueStatement(id, userId));
    }

    @GetMapping("/api/royalties/{id}/statement")
    public ApiResponse<?> getStatement(@PathVariable Long id) {
        return new ApiResponse<>(true, "Royalty statement retrieved", royaltyService.getStatement(id));
    }

    @PostMapping("/api/royalties/{id}/approve")
    public ApiResponse<?> approve(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Royalty approval recorded", royaltyService.approve(id, userId));
    }

    @PostMapping("/api/royalties/{id}/reject")
    public ApiResponse<?> reject(@PathVariable Long id, @Valid @RequestBody RoyaltyRejectRequest request,
                                 Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Royalty statement rejected",
                royaltyService.reject(id, request.getReason(), request.isCancel(), userId));
    }

    @PostMapping("/api/royalties/{id}/pay")
    public ApiResponse<?> pay(@PathVariable Long id, @Valid @RequestBody RoyaltyPayRequest request,
                              Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Royalty payment recorded", royaltyService.pay(id, request, userId));
    }
}

package com.epms.controller;

import com.epms.dto.response.ApiResponse;
import com.epms.dto.response.CatalogResponse;
import com.epms.entity.PublishingApplication;
import com.epms.exception.ResourceNotFoundException;
import com.epms.security.service.CurrentUserService;
import com.epms.service.AccountService;
import com.epms.service.AuthorFinanceService;
import com.epms.service.BookService;
import com.epms.service.DocumentStorageService;
import com.epms.service.PublishingApplicationService;
import com.epms.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Features ported from the team's merged Epic 1 / Epic 2 build: "Getting
 * Published" applications, the author's Contracts & Royalties page, the
 * customer account page, and the public book and author pages.
 */
@RestController
@RequiredArgsConstructor
public class TeamFeaturesController {

    private final PublishingApplicationService applications;
    private final AuthorFinanceService authorFinance;
    private final AccountService accounts;
    private final StoreService store;
    private final BookService bookService;
    private final DocumentStorageService storage;
    private final CurrentUserService currentUserService;

    // ===================== Getting Published =====================

    @PostMapping(value = "/api/publishing-applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> apply(@RequestParam String authorName,
                                @RequestParam(required = false) String contactInformation,
                                @RequestParam String phone,
                                @RequestParam String email,
                                @RequestParam String manuscriptName,
                                @RequestParam String bookType,
                                @RequestParam(required = false) String shortDescription,
                                @RequestParam("manuscriptFile") MultipartFile manuscriptFile) {
        PublishingApplication a = applications.submit(authorName, contactInformation, phone, email, manuscriptName,
                bookType, shortDescription, manuscriptFile);
        return new ApiResponse<>(true, "Your publishing application has been submitted. Keep your application number: "
                + a.getApplicationId(), Map.of("applicationId", a.getApplicationId(), "status", a.getStatus()));
    }

    @GetMapping("/api/publishing-applications/status")
    public ApiResponse<?> applicationStatus(@RequestParam Long applicationId, @RequestParam String email) {
        return new ApiResponse<>(true, "Application status", applications.status(applicationId, email));
    }

    @GetMapping("/api/publishing-applications/admin")
    public ApiResponse<?> allApplications() {
        return new ApiResponse<>(true, "Publishing applications", applications.all());
    }

    @GetMapping("/api/publishing-applications/admin/{id}/file")
    public ResponseEntity<Resource> applicationFile(@PathVariable Long id) {
        PublishingApplication a = applications.get(id);
        if (a.getFilePath() == null) {
            throw new ResourceNotFoundException("No manuscript is attached to application #" + id);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(DocumentStorageService.contentTypeFor(a.getFilePath())))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""
                        + (a.getFileName() == null ? "manuscript" : a.getFileName().replace("\"", "")) + "\"")
                .body(new FileSystemResource(storage.resolve(a.getFilePath())));
    }

    @PostMapping("/api/publishing-applications/{id}/approve")
    public ApiResponse<?> approveApplication(@PathVariable Long id, Authentication auth) {
        return new ApiResponse<>(true, "Application approved. The applicant can now register as an author.",
                applications.approve(id, uid(auth)));
    }

    @PostMapping("/api/publishing-applications/{id}/reject")
    public ApiResponse<?> rejectApplication(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        return new ApiResponse<>(true, "Application rejected", applications.reject(id, body.get("reason"), uid(auth)));
    }

    // ===================== author: contracts, bank details, notifications (US8) =====================

    @GetMapping("/api/author/contracts-royalties")
    public ApiResponse<?> contracts(Authentication auth) {
        return new ApiResponse<>(true, "Contracts and royalties", authorFinance.overview(uid(auth)));
    }

    @PostMapping("/api/author/contracts/{agreementId}/sign")
    public ApiResponse<?> sign(@PathVariable Long agreementId, Authentication auth) {
        return new ApiResponse<>(true, "Agreement signed", authorFinance.sign(uid(auth), agreementId));
    }

    @PutMapping("/api/author/bank-details")
    public ApiResponse<?> bankDetails(@RequestBody Map<String, String> b, Authentication auth) {
        return new ApiResponse<>(true, "Bank details saved", authorFinance.saveBankDetails(uid(auth),
                b.get("accountName"), b.get("bankName"), b.get("accountNumber"), b.get("branch")));
    }

    @PostMapping("/api/author/notifications/{id}/read")
    public ApiResponse<?> read(@PathVariable Long id, Authentication auth) {
        authorFinance.markRead(uid(auth), id);
        return new ApiResponse<>(true, "Marked as read", null);
    }

    /** Finance: where to pay the author. */
    @GetMapping("/api/royalty-agreements/lookup/authors/{authorId}/bank-details")
    public ApiResponse<?> authorBankDetails(@PathVariable Long authorId) {
        return new ApiResponse<>(true, "Bank details", authorFinance.bankDetailsFor(authorId));
    }

    // ===================== customer account =====================

    @GetMapping("/api/customer/profile")
    public ApiResponse<?> profile(Authentication auth) {
        return new ApiResponse<>(true, "Your profile", accounts.profile(uid(auth)));
    }

    @PutMapping("/api/customer/profile")
    public ApiResponse<?> updateProfile(@RequestBody Map<String, String> b, Authentication auth) {
        return new ApiResponse<>(true, "Profile saved",
                accounts.updateProfile(uid(auth), b.get("firstName"), b.get("lastName"), b.get("phoneNumber")));
    }

    /** Any logged-in user: their recent logins. */
    @GetMapping("/api/me/login-activity")
    public ApiResponse<?> loginActivity(Authentication auth) {
        return new ApiResponse<>(true, "Login activity", accounts.loginActivity(uid(auth)));
    }

    /** Guest cart kept in the browser, merged into the account cart after login. */
    @PostMapping("/api/customer/cart/merge")
    public ApiResponse<?> mergeCart(@RequestBody List<Map<String, Object>> items, Authentication auth) {
        Long userId = uid(auth);
        List<String> skipped = new java.util.ArrayList<>();
        for (Map<String, Object> i : items) {
            try {
                Long id = Long.valueOf(String.valueOf(i.get("catalogBookId")));
                int qty = Integer.parseInt(String.valueOf(i.getOrDefault("quantity", 1)));
                store.addToCart(userId, id, Math.max(1, qty));
            } catch (RuntimeException e) {
                skipped.add(e.getMessage());
            }
        }
        Map<String, Object> cart = new LinkedHashMap<>(store.cart(userId));
        cart.put("skipped", skipped);
        return new ApiResponse<>(true, "Cart merged", cart);
    }

    // ===================== public book and author pages =====================

    @GetMapping("/api/public/books/{id}")
    public ApiResponse<?> book(@PathVariable Long id) {
        CatalogResponse.CatalogBook b = bookService.getCatalog().getBooks().stream()
                .filter(x -> x.getBookId().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + id));
        return new ApiResponse<>(true, "Book", b);
    }

    /** Authors with at least one book in the store, and their books. */
    @GetMapping("/api/public/authors")
    public ApiResponse<?> authors() {
        Map<String, List<CatalogResponse.CatalogBook>> byAuthor = bookService.getCatalog().getBooks().stream()
                .collect(Collectors.groupingBy(CatalogResponse.CatalogBook::getAuthor, TreeMap::new, Collectors.toList()));
        List<Map<String, Object>> rows = byAuthor.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("authorName", e.getKey());
            m.put("bookCount", e.getValue().size());
            m.put("coverUrl", e.getValue().stream().map(CatalogResponse.CatalogBook::getCoverUrl)
                    .filter(java.util.Objects::nonNull).findFirst().orElse(null));
            m.put("books", e.getValue());
            return m;
        }).toList();
        return new ApiResponse<>(true, "Authors", rows);
    }

    private Long uid(Authentication auth) {
        return currentUserService.getCurrentUserId(auth);
    }
}

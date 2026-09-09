package com.epms.controller;

import com.epms.dto.response.ApiResponse;
import com.epms.service.RoyaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/royalties")
@RequiredArgsConstructor
public class RoyaltyController {

    private final RoyaltyService royaltyService;

    @GetMapping
    public ApiResponse<?> getAll() {
        return new ApiResponse<>(true, "Royalty calculations retrieved", royaltyService.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<?> getById(@PathVariable Long id) {
        return new ApiResponse<>(true, "Royalty calculation retrieved", royaltyService.getById(id));
    }

    @GetMapping("/author/{authorId}")
    public ApiResponse<?> getByAuthor(@PathVariable Long authorId) {
        return new ApiResponse<>(true, "Royalty calculations retrieved", royaltyService.getByAuthor(authorId));
    }

    @PostMapping("/calculate")
    public ApiResponse<?> calculate(
            @RequestParam Long royaltyAgreementId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return new ApiResponse<>(true, "Royalty calculated",
                royaltyService.calculate(royaltyAgreementId, periodStart, periodEnd));
    }

    @PostMapping("/{id}/recalculate")
    public ApiResponse<?> recalculate(@PathVariable Long id) {
        return new ApiResponse<>(true, "Royalty recalculated", royaltyService.recalculate(id));
    }

    @GetMapping("/{id}/statement")
    public ApiResponse<?> getStatement(@PathVariable Long id) {
        // A statement is a read-only presentation of a finalized calculation.
        return new ApiResponse<>(true, "Royalty statement retrieved", royaltyService.getById(id));
    }
}

package com.epms.controller;

import com.epms.dto.response.ApiResponse;
import com.epms.entity.RoyaltyCalculation;
import com.epms.repository.AnnouncementRepository;
import com.epms.repository.ExpenseRepository;
import com.epms.repository.RoyaltyAgreementRepository;
import com.epms.repository.RoyaltyCalculationRepository;
import com.epms.repository.CategoryRepository;
import com.epms.repository.GenreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CategoryRepository categoryRepository;
    private final GenreRepository genreRepository;
    private final AnnouncementRepository announcementRepository;
    private final RoyaltyAgreementRepository royaltyAgreementRepository;
    private final RoyaltyCalculationRepository royaltyCalculationRepository;
    private final ExpenseRepository expenseRepository;

    @GetMapping("/dashboard")
    public ApiResponse<?> dashboard() {

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCategories", categoryRepository.count());
        stats.put("totalGenres", genreRepository.count());
        stats.put("totalAnnouncements", announcementRepository.count());
        stats.put("activeRoyaltyAgreements", royaltyAgreementRepository.findByStatus("ACTIVE").size());
        stats.put("totalRoyaltyCalculations", royaltyCalculationRepository.count());
        stats.put("totalRoyaltiesCalculated", royaltyCalculationRepository.findAll().stream()
                .map(RoyaltyCalculation::getRoyaltyAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        stats.put("pendingExpenses", expenseRepository.findAll().stream()
                .filter(e -> "RECORDED".equalsIgnoreCase(e.getStatus())).count());
        // Revenue figures come from /api/finance/revenue (US38).

        return new ApiResponse<>(true, "Admin dashboard stats retrieved", stats);
    }
}

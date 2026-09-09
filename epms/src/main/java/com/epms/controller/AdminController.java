package com.epms.controller;

import com.epms.dto.response.ApiResponse;
import com.epms.repository.AnnouncementRepository;
import com.epms.repository.CategoryRepository;
import com.epms.repository.GenreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CategoryRepository categoryRepository;
    private final GenreRepository genreRepository;
    private final AnnouncementRepository announcementRepository;

    @GetMapping("/dashboard")
    public ApiResponse<?> dashboard() {

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCategories", categoryRepository.count());
        stats.put("totalGenres", genreRepository.count());
        stats.put("totalAnnouncements", announcementRepository.count());
        // TODO: revenue/royalty/sales aggregates once Finance & Royalty
        // modules are implemented (see FinanceController, RoyaltyController).

        return new ApiResponse<>(true, "Admin dashboard stats retrieved", stats);
    }
}

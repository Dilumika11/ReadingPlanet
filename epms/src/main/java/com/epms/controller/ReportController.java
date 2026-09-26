package com.epms.controller;

import com.epms.dto.response.ApiResponse;
import com.epms.entity.FinancialReport;
import com.epms.security.service.CurrentUserService;
import com.epms.service.ReportingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportingService reportingService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ApiResponse<?> getAll() {
        return new ApiResponse<>(true, "Reports retrieved", reportingService.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<?> getById(@PathVariable Long id) {
        return new ApiResponse<>(true, "Report retrieved", reportingService.getView(id));
    }

    @PostMapping("/generate")
    public ApiResponse<?> generate(
            @RequestParam String reportType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Report generated",
                reportingService.generate(reportType, periodStart, periodEnd, userId));
    }

    @PostMapping("/{id}/regenerate")
    public ApiResponse<?> regenerate(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Report regenerated", reportingService.regenerate(id, userId));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<?> review(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Report reviewed", reportingService.review(id, userId));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        FinancialReport report = reportingService.getById(id);
        byte[] file = reportingService.download(id);
        String name = "report-" + id + "-" + report.getReportType().toLowerCase() + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + name)
                .body(file);
    }

    @PostMapping("/{id}/finalize")
    public ApiResponse<?> finalizeReport(@PathVariable Long id, Authentication authentication) {
        Long userId = currentUserService.getCurrentUserId(authentication);
        return new ApiResponse<>(true, "Report finalized", reportingService.finalizeReport(id, userId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        reportingService.delete(id);
        return new ApiResponse<>(true, "Report deleted", null);
    }
}

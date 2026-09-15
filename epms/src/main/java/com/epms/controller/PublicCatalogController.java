package com.epms.controller;

import com.epms.dto.response.ApiResponse;
import com.epms.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Unauthenticated endpoints for the public website (see SecurityConfig: /api/public/**). */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicCatalogController {

    private final BookService bookService;

    @GetMapping("/catalog")
    public ApiResponse<?> getCatalog() {
        return new ApiResponse<>(true, "Catalog retrieved", bookService.getCatalog());
    }
}

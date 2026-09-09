package com.epms.controller;

import com.epms.dto.request.CategoryRequest;
import com.epms.dto.response.ApiResponse;
import com.epms.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ApiResponse<?> getAll() {
        return new ApiResponse<>(true, "Categories retrieved", categoryService.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<?> getById(@PathVariable Long id) {
        return new ApiResponse<>(true, "Category retrieved", categoryService.getById(id));
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody CategoryRequest request) {
        return new ApiResponse<>(true, "Category created", categoryService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return new ApiResponse<>(true, "Category updated", categoryService.update(id, request));
    }

    @PatchMapping("/{id}/archive")
    public ApiResponse<?> archive(@PathVariable Long id) {
        return new ApiResponse<>(true, "Category archived", categoryService.archive(id));
    }
}

package com.epms.controller;

import com.epms.dto.request.GenreRequest;
import com.epms.dto.response.ApiResponse;
import com.epms.service.GenreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/genres")
@RequiredArgsConstructor
public class GenreController {

    private final GenreService genreService;

    @GetMapping
    public ApiResponse<?> getAll() {
        return new ApiResponse<>(true, "Genres retrieved", genreService.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<?> getById(@PathVariable Long id) {
        return new ApiResponse<>(true, "Genre retrieved", genreService.getById(id));
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody GenreRequest request) {
        return new ApiResponse<>(true, "Genre created", genreService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody GenreRequest request) {
        return new ApiResponse<>(true, "Genre updated", genreService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        genreService.delete(id);
        return new ApiResponse<>(true, "Genre deleted", null);
    }
}

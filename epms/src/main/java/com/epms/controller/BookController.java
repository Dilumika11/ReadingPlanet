package com.epms.controller;

import com.epms.dto.request.BookRequest;
import com.epms.dto.response.ApiResponse;
import com.epms.service.BookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Admin management of the online-store catalogue. */
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @GetMapping
    public ApiResponse<?> getAll() {
        return new ApiResponse<>(true, "Books retrieved", bookService.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<?> getById(@PathVariable Long id) {
        return new ApiResponse<>(true, "Book retrieved", bookService.getById(id));
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody BookRequest request) {
        return new ApiResponse<>(true, "Book created", bookService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        return new ApiResponse<>(true, "Book updated", bookService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        bookService.delete(id);
        return new ApiResponse<>(true, "Book deleted", null);
    }

    @PostMapping(value = "/{id}/cover", consumes = "multipart/form-data")
    public ApiResponse<?> uploadCover(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return new ApiResponse<>(true, "Cover uploaded", bookService.uploadCover(id, file));
    }
}

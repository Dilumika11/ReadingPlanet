package com.epms.service;

import com.epms.dto.request.BookRequest;
import com.epms.dto.response.CatalogResponse;
import com.epms.entity.Book;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Online-store catalogue: admin-managed books in {@code catalog_books},
 * grouped by the admin-managed categories/genres.
 */
public interface BookService {

    // --- Admin ---
    List<Book> getAll();
    Book getById(Long id);
    Book create(BookRequest request);
    Book update(Long id, BookRequest request);
    /** Hard delete; also removes the stored cover file. */
    void delete(Long id);

    /** Stores the image and links it to the book; replaces any previous cover. */
    Book uploadCover(Long id, MultipartFile file);

    /**
     * Re-attaches cover files found on disk (named {@code book-<id>-*}) to
     * books that have no cover recorded, e.g. after the database was reset
     * while the uploads folder was kept. Returns how many were relinked.
     */
    int relinkOrphanedCovers();

    // --- Public store ---
    /** Categories with counts + all books, ready for the store page. */
    CatalogResponse getCatalog();

    /** Public URL for a stored cover file name, or null. */
    String coverUrl(String coverImage);
}

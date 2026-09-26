package com.epms.service;

import com.epms.entity.Book;
import com.epms.entity.Manuscript;
import com.epms.entity.PublishedBook;
import com.epms.repository.BookRepository;
import com.epms.repository.ManuscriptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Keeps the online-store listing (catalog_books) in step with published
 * books: when a book's first stock arrives it gets a store listing linked
 * to it, with the approved cover, so customers can order it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogSync {

    private final BookRepository catalogRepository;
    private final ManuscriptRepository manuscriptRepository;
    private final DocumentStorageService storage;
    private final ManuscriptWorkflow workflow;
    private final UserNames names;

    @Value("${epms.uploads.dir:uploads}")
    private String uploadsDir;

    public Book onPublished(PublishedBook book, Long userId) {
        Manuscript m = manuscriptRepository.findById(book.getManuscriptId()).orElse(null);
        if (m != null && !"PUBLISHED".equals(m.getStatus())) {
            workflow.changeStatus(m, "PUBLISHED", userId, "First stock received; book is on sale");
        }
        Book existing = catalogRepository.findFirstByStockBookId(book.getBookId()).orElse(null);
        if (existing != null) {
            return existing;
        }
        Book listing = new Book();
        listing.setTitle(book.getTitle());
        listing.setAuthorName(m == null ? "Unknown" : names.author(m.getAuthorId()));
        listing.setAuthorId(m == null ? null : m.getAuthorId());
        listing.setCategoryId(book.getCategoryId());
        listing.setGenreId(book.getGenreId());
        listing.setPrice(book.getPrice());
        listing.setIsbn(book.getIsbn());
        listing.setDescription(m == null || m.getSynopsis() == null ? null
                : m.getSynopsis().length() > 2000 ? m.getSynopsis().substring(0, 2000) : m.getSynopsis());
        listing.setNewArrival(true);
        listing.setStockBookId(book.getBookId());
        Book saved = catalogRepository.save(listing);
        copyCover(book, saved);
        return saved;
    }

    /** The approved cover is private until publication; publish a copy for the store. */
    private void copyCover(PublishedBook book, Book listing) {
        if (book.getCoverImage() == null) return;
        try {
            Path src = storage.resolve(book.getCoverImage());
            String ext = book.getCoverImage().substring(book.getCoverImage().lastIndexOf('.') + 1);
            String name = "book-" + listing.getBookId() + "-" + UUID.randomUUID().toString().substring(0, 8) + "." + ext;
            Path dir = Paths.get(uploadsDir, "covers").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Files.copy(src, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            listing.setCoverImage(name);
            catalogRepository.save(listing);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not publish the cover of book {}: {}", book.getBookId(), e.getMessage());
        }
    }
}

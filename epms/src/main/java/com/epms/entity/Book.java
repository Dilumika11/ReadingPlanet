package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A book in the online-store catalogue, managed from the admin panel.
 *
 * Stored in {@code catalog_books} (not Epic 2's {@code books}, which is tied
 * to the manuscript/production pipeline). Category and genre reference the
 * admin-managed tables by id, so the store follows renames/deletions there.
 * All text columns are Unicode (utf8mb4 on MySQL) so Sinhala titles and
 * author names are stored as-is.
 */
@Entity
@Table(name = "catalog_books")
@Data
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_id")
    private Long bookId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "author_name", nullable = false, length = 150)
    private String authorName;

    // Optional link to Epic 1's author (used by royalty agreements); not owned here.
    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "genre_id")
    private Long genreId;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "isbn", length = 20)
    private String isbn;

    @Column(name = "description", length = 2000)
    private String description;

    // File name inside the uploads/covers directory; null = no cover yet.
    @Column(name = "cover_image", length = 255)
    private String coverImage;

    // The produced book (books.book_id) whose stock is sold; null = not orderable yet
    @Column(name = "stock_book_id")
    private Long stockBookId;

    @Column(name = "new_arrival", nullable = false)
    private boolean newArrival = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

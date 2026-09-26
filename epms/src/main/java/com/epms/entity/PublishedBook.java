package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A produced book (shared table {@code books}), created when production
 * marks a manuscript Ready for Printing (US20). Printing, stock and orders
 * all refer to this record. Status: READY_FOR_PRINTING -> PUBLISHED (first
 * stock received). Not the same as {@link Book}, the store listing.
 */
@Entity
@Table(name = "books")
@Data
public class PublishedBook {

    public static final String READY_FOR_PRINTING = "READY_FOR_PRINTING";
    public static final String PUBLISHED = "PUBLISHED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_id")
    private Long bookId;

    @Column(name = "manuscript_id", nullable = false)
    private Long manuscriptId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "genre_id", nullable = false)
    private Long genreId;

    @Column(name = "isbn", nullable = false, unique = true, length = 20)
    private String isbn;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "edition", length = 50)
    private String edition;

    @Column(name = "cover_image", length = 500)
    private String coverImage;

    @Column(name = "publication_date")
    private LocalDate publicationDate;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "total_pages")
    private Integer totalPages;

    @Column(name = "language", nullable = false, length = 50)
    private String language;

    @Column(name = "book_status", nullable = false, length = 30)
    private String bookStatus = READY_FOR_PRINTING;

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

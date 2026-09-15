package com.epms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** What the public online-store page renders: active categories + the books in them. */
@Data
@AllArgsConstructor
public class CatalogResponse {

    private List<CatalogCategory> categories;
    private List<CatalogBook> books;

    @Data
    @AllArgsConstructor
    public static class CatalogCategory {
        private Long categoryId;
        private String categoryName;
        private String description;
        private long bookCount;
    }

    @Data
    @AllArgsConstructor
    public static class CatalogBook {
        private Long bookId;
        private String title;
        private String author;
        private Long authorId;
        /** Null when the book has no category (e.g. its genre/category was removed). */
        private Long categoryId;
        private String categoryName;
        private String genreName;
        private BigDecimal price;
        private String coverUrl;
        private boolean newArrival;
        private String blurb;
    }
}

package com.epms.config;

import com.epms.config.DemoCatalog.DemoBook;
import com.epms.entity.Book;
import com.epms.entity.Category;
import com.epms.entity.Genre;
import com.epms.repository.BookRepository;
import com.epms.repository.CategoryRepository;
import com.epms.repository.GenreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds the store catalogue from {@link DemoCatalog} — creating the
 * categories/genres it needs in the admin-managed tables — so the online
 * store has books to show on a fresh dev database. Runs before the sales
 * seeder (which references these book ids) and only when the table is empty.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "epms.demo-data.enabled", havingValue = "true")
public class DemoBookInitializer implements CommandLineRunner {

    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;
    private final GenreRepository genreRepository;

    @Override
    public void run(String... args) {
        if (bookRepository.count() > 0) {
            log.info("Demo catalogue: catalog_books already populated, skipping seed");
            return;
        }

        for (DemoBook d : DemoCatalog.BOOKS) {
            Book book = new Book();
            book.setTitle(d.title());
            book.setAuthorName(d.author());
            book.setAuthorId(d.authorId());
            book.setCategoryId(categoryId(d.category()));
            book.setGenreId(genreId(d.genre()));
            book.setPrice(d.price());
            book.setDescription(d.blurb());
            book.setNewArrival(d.newArrival());
            bookRepository.save(book);
        }
        log.info("Demo catalogue: seeded {} books into catalog_books", DemoCatalog.BOOKS.size());
    }

    private Long categoryId(String name) {
        return categoryRepository.findByCategoryNameIgnoreCase(name)
                .map(Category::getCategoryId)
                .orElseGet(() -> {
                    Category c = new Category();
                    c.setCategoryName(name);
                    c.setDescription("Seeded for the demo catalogue");
                    return categoryRepository.save(c).getCategoryId();
                });
    }

    private Long genreId(String name) {
        return genreRepository.findByGenreNameIgnoreCase(name)
                .map(Genre::getGenreId)
                .orElseGet(() -> {
                    Genre g = new Genre();
                    g.setGenreName(name);
                    g.setDescription("Seeded for the demo catalogue");
                    return genreRepository.save(g).getGenreId();
                });
    }
}

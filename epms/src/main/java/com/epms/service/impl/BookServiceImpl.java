package com.epms.service.impl;

import com.epms.dto.request.BookRequest;
import com.epms.dto.response.CatalogResponse;
import com.epms.entity.Book;
import com.epms.entity.Category;
import com.epms.entity.Genre;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.BookRepository;
import com.epms.repository.CategoryRepository;
import com.epms.repository.GenreRepository;
import com.epms.service.BookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BookServiceImpl implements BookService {

    public static final String COVER_URL_PREFIX = "/uploads/covers/";

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Map<String, String> EXTENSION_BY_TYPE = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;
    private final GenreRepository genreRepository;

    @Value("${epms.uploads.dir:uploads}")
    private String uploadsDir;

    // --- Admin ---

    @Override
    @Transactional(readOnly = true)
    public List<Book> getAll() {
        return bookRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public Book getById(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + id));
    }

    @Override
    public Book create(BookRequest request) {
        Book book = new Book();
        apply(book, request);
        return bookRepository.save(book);
    }

    @Override
    public Book update(Long id, BookRequest request) {
        Book book = getById(id);
        apply(book, request);
        return bookRepository.save(book);
    }

    @Override
    public void delete(Long id) {
        Book book = getById(id);
        if (book.getCoverImage() != null) {
            try {
                Files.deleteIfExists(coversDir().resolve(book.getCoverImage()));
            } catch (IOException e) {
                log.warn("Could not delete cover {} for book {}: {}", book.getCoverImage(), id, e.getMessage());
            }
        }
        bookRepository.delete(book);
    }

    @Override
    public Book uploadCover(Long id, MultipartFile file) {
        Book book = getById(id);

        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Choose an image file to upload");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new BusinessRuleException("Cover must be a JPEG, PNG or WebP image (got " + contentType + ")");
        }

        String fileName = "book-" + id + "-" + UUID.randomUUID().toString().substring(0, 8)
                + "." + EXTENSION_BY_TYPE.get(contentType);
        try {
            Path dir = coversDir();
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            if (book.getCoverImage() != null) {
                Files.deleteIfExists(dir.resolve(book.getCoverImage()));
            }
        } catch (IOException e) {
            throw new BusinessRuleException("Could not store the cover image: " + e.getMessage());
        }

        book.setCoverImage(fileName);
        return bookRepository.save(book);
    }

    @Override
    public int relinkOrphanedCovers() {
        Path dir = coversDir();
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int relinked = 0;
        for (Book book : bookRepository.findAll()) {
            if (book.getCoverImage() != null && Files.exists(dir.resolve(book.getCoverImage()))) {
                continue;
            }
            String prefix = "book-" + book.getBookId() + "-";
            try (Stream<Path> files = Files.list(dir)) {
                Optional<Path> newest = files
                        .filter(p -> p.getFileName().toString().startsWith(prefix))
                        .max(Comparator.comparingLong(p -> p.toFile().lastModified()));
                if (newest.isPresent()) {
                    book.setCoverImage(newest.get().getFileName().toString());
                    bookRepository.save(book);
                    relinked++;
                } else if (book.getCoverImage() != null) {
                    // Recorded file is gone — clear the dangling reference.
                    book.setCoverImage(null);
                    bookRepository.save(book);
                }
            } catch (IOException e) {
                log.warn("Could not scan covers directory {}: {}", dir, e.getMessage());
                return relinked;
            }
        }
        if (relinked > 0) {
            log.info("Relinked {} book cover(s) found in {}", relinked, dir);
        }
        return relinked;
    }

    private void apply(Book book, BookRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.getCategoryId()));
        if (request.getGenreId() != null && !genreRepository.existsById(request.getGenreId())) {
            throw new ResourceNotFoundException("Genre not found: " + request.getGenreId());
        }

        book.setTitle(request.getTitle().trim());
        book.setAuthorName(request.getAuthorName().trim());
        book.setAuthorId(request.getAuthorId());
        book.setCategoryId(category.getCategoryId());
        book.setGenreId(request.getGenreId());
        book.setPrice(request.getPrice());
        book.setIsbn(blankToNull(request.getIsbn()));
        book.setDescription(blankToNull(request.getDescription()));
        book.setNewArrival(request.isNewArrival());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private Path coversDir() {
        return Paths.get(uploadsDir, "covers").toAbsolutePath().normalize();
    }

    // --- Public store ---

    @Override
    @Transactional(readOnly = true)
    public CatalogResponse getCatalog() {
        Map<Long, Category> categories = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getCategoryId, Function.identity()));
        Map<Long, Genre> genres = genreRepository.findAll().stream()
                .collect(Collectors.toMap(Genre::getGenreId, Function.identity()));

        List<CatalogResponse.CatalogBook> books = bookRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(b -> {
                    Category category = b.getCategoryId() == null ? null : categories.get(b.getCategoryId());
                    Genre genre = b.getGenreId() == null ? null : genres.get(b.getGenreId());
                    return new CatalogResponse.CatalogBook(
                            b.getBookId(), b.getTitle(), b.getAuthorName(), b.getAuthorId(),
                            category == null ? null : category.getCategoryId(),
                            category == null ? null : category.getCategoryName(),
                            genre == null ? null : genre.getGenreName(),
                            b.getPrice(), coverUrl(b.getCoverImage()), b.isNewArrival(), b.getDescription());
                })
                .collect(Collectors.toList());

        Map<Long, Long> countByCategory = books.stream()
                .filter(b -> b.getCategoryId() != null)
                .collect(Collectors.groupingBy(CatalogResponse.CatalogBook::getCategoryId, Collectors.counting()));

        List<CatalogResponse.CatalogCategory> activeCategories = categories.values().stream()
                .sorted((a, b) -> a.getCategoryName().compareToIgnoreCase(b.getCategoryName()))
                .map(c -> new CatalogResponse.CatalogCategory(
                        c.getCategoryId(), c.getCategoryName(), c.getDescription(),
                        countByCategory.getOrDefault(c.getCategoryId(), 0L)))
                .collect(Collectors.toList());

        return new CatalogResponse(activeCategories, books);
    }

    @Override
    public String coverUrl(String coverImage) {
        return coverImage == null ? null : COVER_URL_PREFIX + coverImage;
    }
}

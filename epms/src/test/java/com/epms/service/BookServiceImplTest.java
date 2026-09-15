package com.epms.service;

import com.epms.entity.Book;
import com.epms.exception.BusinessRuleException;
import com.epms.repository.BookRepository;
import com.epms.repository.CategoryRepository;
import com.epms.repository.GenreRepository;
import com.epms.service.impl.BookServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Book cover storage: upload validation, on-disk layout, and relinking after a DB reset. */
@ExtendWith(MockitoExtension.class)
class BookServiceImplTest {

    @Mock BookRepository bookRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock GenreRepository genreRepository;

    @InjectMocks BookServiceImpl service;

    @TempDir Path uploads;

    private Book book;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "uploadsDir", uploads.toString());
        book = new Book();
        book.setBookId(7L);
        book.setTitle("සයිකෝ");
        book.setAuthorName("සුසිත් රුවන්");
        book.setPrice(new BigDecimal("1800.00"));
    }

    @Test
    void uploadStoresFileUnderCoversAndLinksIt() throws IOException {
        when(bookRepository.findById(7L)).thenReturn(Optional.of(book));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile png = new MockMultipartFile("file", "cover.png", "image/png", new byte[] {1, 2, 3, 4});

        Book saved = service.uploadCover(7L, png);

        assertThat(saved.getCoverImage()).startsWith("book-7-").endsWith(".png");
        Path stored = uploads.resolve("covers").resolve(saved.getCoverImage());
        assertThat(stored).exists();
        assertThat(Files.readAllBytes(stored)).containsExactly(1, 2, 3, 4);
        assertThat(service.coverUrl(saved.getCoverImage())).isEqualTo("/uploads/covers/" + saved.getCoverImage());
    }

    @Test
    void uploadReplacesThePreviousCoverFile() throws IOException {
        Path dir = Files.createDirectories(uploads.resolve("covers"));
        Files.write(dir.resolve("book-7-old.png"), new byte[] {9});
        book.setCoverImage("book-7-old.png");
        when(bookRepository.findById(7L)).thenReturn(Optional.of(book));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        Book saved = service.uploadCover(7L, new MockMultipartFile("file", "c.jpg", "image/jpeg", new byte[] {1}));

        assertThat(saved.getCoverImage()).endsWith(".jpg");
        assertThat(dir.resolve("book-7-old.png")).doesNotExist();
        assertThat(dir.resolve(saved.getCoverImage())).exists();
    }

    @Test
    void rejectsNonImageUploads() {
        when(bookRepository.findById(7L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> service.uploadCover(7L,
                new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1})))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("JPEG, PNG or WebP");
        assertThatThrownBy(() -> service.uploadCover(7L,
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0])))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Choose an image");

        verify(bookRepository, never()).save(any());
    }

    @Test
    void relinkFindsCoverFileForBookWithNoCoverRecorded() throws IOException {
        // Simulates a DB reset: the file is on disk but the book row has no cover_image.
        Path dir = Files.createDirectories(uploads.resolve("covers"));
        Files.write(dir.resolve("book-7-abc12345.png"), new byte[] {1});
        Files.write(dir.resolve("book-99-zzz.png"), new byte[] {1});
        when(bookRepository.findAll()).thenReturn(List.of(book));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        int relinked = service.relinkOrphanedCovers();

        assertThat(relinked).isEqualTo(1);
        assertThat(book.getCoverImage()).isEqualTo("book-7-abc12345.png");
    }

    @Test
    void relinkClearsDanglingReferenceWhenFileIsGone() throws IOException {
        Files.createDirectories(uploads.resolve("covers"));
        book.setCoverImage("book-7-deleted.png");
        when(bookRepository.findAll()).thenReturn(List.of(book));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.relinkOrphanedCovers()).isZero();
        assertThat(book.getCoverImage()).isNull();
    }

    @Test
    void relinkIsNoOpWhenCoversDirectoryDoesNotExist() {
        assertThat(service.relinkOrphanedCovers()).isZero();
        verify(bookRepository, never()).findAll();
    }
}

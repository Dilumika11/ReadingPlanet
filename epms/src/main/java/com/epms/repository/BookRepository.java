package com.epms.repository;

import com.epms.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findAllByOrderByCreatedAtDesc();

    long countByCategoryId(Long categoryId);

    List<Book> findByGenreId(Long genreId);
}

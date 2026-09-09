package com.epms.repository;

import com.epms.entity.Genre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, Long> {

    boolean existsByGenreNameIgnoreCase(String genreName);

    Optional<Genre> findByGenreNameIgnoreCase(String genreName);
}

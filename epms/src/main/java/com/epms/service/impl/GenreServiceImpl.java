package com.epms.service.impl;

import com.epms.dto.request.GenreRequest;
import com.epms.entity.Genre;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.entity.Book;
import com.epms.repository.BookRepository;
import com.epms.repository.GenreRepository;
import com.epms.service.GenreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GenreServiceImpl implements GenreService {

    private final GenreRepository genreRepository;
    private final BookRepository bookRepository;

    @Override
    public List<Genre> getAll() {
        return genreRepository.findAll();
    }

    @Override
    public Genre getById(Long id) {
        return genreRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Genre not found: " + id));
    }

    @Override
    public Genre create(GenreRequest request) {

        if (genreRepository.existsByGenreNameIgnoreCase(request.getGenreName())) {
            throw new BusinessRuleException("Genre name already exists: " + request.getGenreName());
        }

        Genre genre = new Genre();
        genre.setGenreName(request.getGenreName());
        genre.setDescription(request.getDescription());

        return genreRepository.save(genre);
    }

    @Override
    public Genre update(Long id, GenreRequest request) {

        Genre genre = getById(id);

        if (!genre.getGenreName().equalsIgnoreCase(request.getGenreName())
                && genreRepository.existsByGenreNameIgnoreCase(request.getGenreName())) {
            throw new BusinessRuleException("Genre name already exists: " + request.getGenreName());
        }

        genre.setGenreName(request.getGenreName());
        genre.setDescription(request.getDescription());

        return genreRepository.save(genre);
    }

    @Override
    public void delete(Long id) {

        Genre genre = getById(id);

        // Genre is optional on a book, so detach rather than block.
        for (Book book : bookRepository.findByGenreId(id)) {
            book.setGenreId(null);
            bookRepository.save(book);
        }

        genreRepository.delete(genre);
    }
}

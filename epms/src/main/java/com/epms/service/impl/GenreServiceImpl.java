package com.epms.service.impl;

import com.epms.dto.request.GenreRequest;
import com.epms.entity.Genre;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.GenreRepository;
import com.epms.service.GenreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GenreServiceImpl implements GenreService {

    private final GenreRepository genreRepository;

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
    public Genre archive(Long id) {

        Genre genre = getById(id);
        genre.setStatus("ARCHIVED");

        return genreRepository.save(genre);
    }
}

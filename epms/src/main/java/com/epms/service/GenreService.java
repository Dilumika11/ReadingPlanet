package com.epms.service;

import com.epms.dto.request.GenreRequest;
import com.epms.entity.Genre;

import java.util.List;

public interface GenreService {

    List<Genre> getAll();

    Genre getById(Long id);

    Genre create(GenreRequest request);

    Genre update(Long id, GenreRequest request);

    Genre archive(Long id);
}

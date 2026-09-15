package com.epms.service;

import com.epms.dto.request.CategoryRequest;
import com.epms.entity.Category;

import java.util.List;

public interface CategoryService {

    List<Category> getAll();

    Category getById(Long id);

    Category create(CategoryRequest request);

    Category update(Long id, CategoryRequest request);

    /** Hard delete; refused while any book still uses the category. */
    void delete(Long id);
}

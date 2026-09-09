package com.epms.service.impl;

import com.epms.dto.request.CategoryRequest;
import com.epms.entity.Category;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.CategoryRepository;
import com.epms.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    public List<Category> getAll() {
        return categoryRepository.findAll();
    }

    @Override
    public Category getById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    @Override
    public Category create(CategoryRequest request) {

        if (categoryRepository.existsByCategoryNameIgnoreCase(request.getCategoryName())) {
            throw new BusinessRuleException("Category name already exists: " + request.getCategoryName());
        }

        Category category = new Category();
        category.setCategoryName(request.getCategoryName());
        category.setDescription(request.getDescription());

        return categoryRepository.save(category);
    }

    @Override
    public Category update(Long id, CategoryRequest request) {

        Category category = getById(id);

        if (!category.getCategoryName().equalsIgnoreCase(request.getCategoryName())
                && categoryRepository.existsByCategoryNameIgnoreCase(request.getCategoryName())) {
            throw new BusinessRuleException("Category name already exists: " + request.getCategoryName());
        }

        category.setCategoryName(request.getCategoryName());
        category.setDescription(request.getDescription());

        return categoryRepository.save(category);
    }

    @Override
    public Category archive(Long id) {

        Category category = getById(id);
        category.setStatus("ARCHIVED");

        return categoryRepository.save(category);
    }
}

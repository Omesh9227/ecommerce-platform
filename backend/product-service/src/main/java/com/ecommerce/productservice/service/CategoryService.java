package com.ecommerce.productservice.service;

import com.ecommerce.productservice.dto.request.CategoryRequest;
import com.ecommerce.productservice.dto.response.CategoryResponse;
import com.ecommerce.productservice.dto.response.CategoryTreeNode;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CategoryService {

    List<CategoryTreeNode> getTree();

    CategoryResponse getCategory(UUID id);

    CategoryResponse create(CategoryRequest request);

    CategoryResponse update(UUID id, CategoryRequest request);

    void delete(UUID id);

    /**
     * The category plus all its sub-categories (any depth). Used so that filtering by "Electronics"
     * also returns products in "Mobiles". Empty if the category does not exist.
     */
    Set<UUID> getSelfAndDescendantIds(UUID id);
}

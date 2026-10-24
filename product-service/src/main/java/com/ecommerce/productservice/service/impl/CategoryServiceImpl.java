package com.ecommerce.productservice.service.impl;

import com.ecommerce.productservice.dto.request.CategoryRequest;
import com.ecommerce.productservice.dto.response.CategoryResponse;
import com.ecommerce.productservice.dto.response.CategorySummary;
import com.ecommerce.productservice.dto.response.CategoryTreeNode;
import com.ecommerce.productservice.entity.Category;
import com.ecommerce.productservice.event.EventTypes;
import com.ecommerce.productservice.exception.BadRequestException;
import com.ecommerce.productservice.exception.ConflictException;
import com.ecommerce.productservice.exception.ResourceNotFoundException;
import com.ecommerce.productservice.outbox.ProductEventPublisher;
import com.ecommerce.productservice.repository.CategoryRepository;
import com.ecommerce.productservice.repository.ProductRepository;
import com.ecommerce.productservice.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final String CATEGORY = "Category";
    private static final Comparator<Category> BY_NAME = Comparator.comparing(c -> c.getName().toLowerCase(Locale.ROOT));

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductEventPublisher eventPublisher;

    // ---------- Read ----------

    @Override
    @Transactional(readOnly = true)
    public List<CategoryTreeNode> getTree() {
        Map<UUID, List<Category>> childrenByParent = childrenByParent(categoryRepository.findAll());
        return buildNodes(childrenByParent, null);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategory(UUID id) {
        Category category = findCategory(id);
        List<Category> all = categoryRepository.findAll();
        return toResponse(category, childrenByParent(all).getOrDefault(id, List.of()));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> getSelfAndDescendantIds(UUID id) {
        List<Category> all = categoryRepository.findAll();
        boolean exists = all.stream().anyMatch(c -> c.getId().equals(id));
        if (!exists) {
            return Set.of();
        }
        return selfAndDescendants(id, childrenByParent(all));
    }

    // ---------- Write ----------

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        Category parent = request.parentId() == null ? null : findParent(request.parentId());
        ensureUniqueSiblingName(name, request.parentId(), null);

        Category category = Category.builder()
                .name(name)
                .description(trimToNull(request.description()))
                .parent(parent)
                .build();

        Category saved = saveAndFlush(category);
        eventPublisher.category(EventTypes.CATEGORY_CREATED, saved, versionOf(saved));
        log.info("Category created id={} parentId={}", saved.getId(), request.parentId());
        return toResponse(saved, List.of());
    }

    @Override
    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = findCategory(id);
        String name = request.name().trim();
        UUID newParentId = request.parentId();

        Category parent = null;
        if (newParentId != null) {
            List<Category> all = categoryRepository.findAll();
            Set<UUID> ownSubtree = selfAndDescendants(id, childrenByParent(all));
            if (ownSubtree.contains(newParentId)) {
                throw new BadRequestException("A category cannot be moved under itself or one of its sub-categories");
            }
            parent = findParent(newParentId);
        }
        ensureUniqueSiblingName(name, newParentId, id);

        category.setName(name);
        category.setDescription(trimToNull(request.description()));
        category.setParent(parent);

        Category saved = saveAndFlush(category);
        eventPublisher.category(EventTypes.CATEGORY_UPDATED, saved, versionOf(saved));
        log.info("Category updated id={}", id);

        List<Category> children = childrenByParent(categoryRepository.findAll()).getOrDefault(id, List.of());
        return toResponse(saved, children);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Category category = findCategory(id);
        if (categoryRepository.existsByParent_Id(id)) {
            throw new ConflictException("Category has sub-categories. Delete or move them first.");
        }
        if (productRepository.existsByCategory_Id(id)) {
            throw new ConflictException("Category has products. Move or delete them first.");
        }
        eventPublisher.category(EventTypes.CATEGORY_DELETED, category, versionOf(category) + 1);
        categoryRepository.delete(category);
        log.info("Category deleted id={}", id);
    }

    // ---------- Helpers ----------

    private static long versionOf(Category category) {
        return category.getVersion() == null ? 0 : category.getVersion();
    }

    private Category findCategory(UUID id) {
        return categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException(CATEGORY, id));
    }

    private Category findParent(UUID parentId) {
        return categoryRepository.findById(parentId)
                .orElseThrow(() -> new BadRequestException("Parent category not found with id: " + parentId));
    }

    private void ensureUniqueSiblingName(String name, UUID parentId, UUID excludeId) {
        boolean exists;
        if (parentId == null) {
            exists = excludeId == null
                    ? categoryRepository.existsByParentIsNullAndNameIgnoreCase(name)
                    : categoryRepository.existsByParentIsNullAndNameIgnoreCaseAndIdNot(name, excludeId);
        } else {
            exists = excludeId == null
                    ? categoryRepository.existsByParent_IdAndNameIgnoreCase(parentId, name)
                    : categoryRepository.existsByParent_IdAndNameIgnoreCaseAndIdNot(parentId, name, excludeId);
        }
        if (exists) {
            throw new ConflictException("A category named '" + name + "' already exists at this level");
        }
    }

    private Category saveAndFlush(Category category) {
        try {
            return categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent create with the same name: the unique index catches what the check above missed
            throw new ConflictException("A category named '" + category.getName() + "' already exists at this level");
        }
    }

    private static Map<UUID, List<Category>> childrenByParent(List<Category> all) {
        Map<UUID, List<Category>> map = new HashMap<>();
        for (Category category : all) {
            map.computeIfAbsent(category.parentIdOrNull(), key -> new ArrayList<>()).add(category);
        }
        map.values().forEach(list -> list.sort(BY_NAME));
        return map;
    }

    private static List<CategoryTreeNode> buildNodes(Map<UUID, List<Category>> childrenByParent, UUID parentId) {
        List<CategoryTreeNode> nodes = new ArrayList<>();
        for (Category category : childrenByParent.getOrDefault(parentId, List.of())) {
            nodes.add(new CategoryTreeNode(category.getId(), category.getName(), category.getDescription(),
                    buildNodes(childrenByParent, category.getId())));
        }
        return nodes;
    }

    /** Breadth-first walk; the visited set also protects against a corrupted (cyclic) tree. */
    private static Set<UUID> selfAndDescendants(UUID rootId, Map<UUID, List<Category>> childrenByParent) {
        Set<UUID> result = new LinkedHashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (result.add(current)) {
                for (Category child : childrenByParent.getOrDefault(current, List.of())) {
                    queue.add(child.getId());
                }
            }
        }
        return result;
    }

    private static CategoryResponse toResponse(Category category, List<Category> children) {
        Category parent = category.getParent();
        CategorySummary parentSummary = parent == null ? null : new CategorySummary(parent.getId(), parent.getName());
        List<CategorySummary> childSummaries = children.stream()
                .map(child -> new CategorySummary(child.getId(), child.getName()))
                .toList();
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(),
                parentSummary, childSummaries, category.getCreatedAt(), category.getUpdatedAt());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

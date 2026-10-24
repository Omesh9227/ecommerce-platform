package com.ecommerce.productservice.event;

import com.ecommerce.productservice.entity.Category;

import java.time.Instant;
import java.util.UUID;

/**
 * Published on category-events (key = category id) for CATEGORY_CREATED / CATEGORY_UPDATED / CATEGORY_DELETED, so
 * search-service knows each category's name and place in the tree (a category filter includes its sub-categories).
 * categoryVersion increases with every change (CATEGORY_DELETED: one more than the last update).
 */
public record CategoryEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID categoryId,
        String name,
        UUID parentId,
        long categoryVersion
) {

    public static CategoryEvent of(String eventType, Category category, long version) {
        return new CategoryEvent(UUID.randomUUID(), eventType, Instant.now(), category.getId(), category.getName(),
                category.parentIdOrNull(), version);
    }
}

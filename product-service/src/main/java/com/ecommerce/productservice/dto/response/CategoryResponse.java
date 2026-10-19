package com.ecommerce.productservice.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One category with its parent and direct children. */
public record CategoryResponse(
        UUID id,
        String name,
        String description,
        CategorySummary parent,
        List<CategorySummary> children,
        Instant createdAt,
        Instant updatedAt
) {
}

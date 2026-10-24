package com.ecommerce.searchservice.event;

import java.util.UUID;

/** product-service's category-events. */
public record CategoryEvent(
        UUID eventId,
        String eventType,
        UUID categoryId,
        String name,
        UUID parentId,
        long categoryVersion
) {
}

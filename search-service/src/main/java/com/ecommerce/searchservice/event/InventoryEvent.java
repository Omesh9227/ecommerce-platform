package com.ecommerce.searchservice.event;

import java.util.UUID;

/** The fields of inventory-service's inventory-events that search needs (stock for "in stock" and "only 2 left"). */
public record InventoryEvent(
        UUID eventId,
        String eventType,
        UUID productId,
        int availableQuantity,
        long inventoryVersion
) {
}

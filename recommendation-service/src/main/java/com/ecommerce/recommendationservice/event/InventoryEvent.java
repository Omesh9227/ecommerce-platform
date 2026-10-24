package com.ecommerce.recommendationservice.event;

import java.util.UUID;

/** The fields of inventory-service's inventory-events that recommendations need (sold-out products are never recommended). */
public record InventoryEvent(
        UUID eventId,
        String eventType,
        UUID productId,
        int availableQuantity,
        long inventoryVersion
) {
}

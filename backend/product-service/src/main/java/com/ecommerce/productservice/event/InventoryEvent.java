package com.ecommerce.productservice.event;

import java.util.UUID;

/**
 * Consumed from inventory-events (published by inventory-service). Only the fields product-service needs;
 * other fields in the JSON are ignored.
 */
public record InventoryEvent(
        UUID eventId,
        String eventType,
        UUID productId,
        int availableQuantity,
        long inventoryVersion
) {
}

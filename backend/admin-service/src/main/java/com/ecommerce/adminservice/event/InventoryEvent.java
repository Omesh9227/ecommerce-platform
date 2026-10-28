package com.ecommerce.adminservice.event;

import java.time.Instant;
import java.util.UUID;

/** inventory-service's inventory-events: current stock of a product. */
public record InventoryEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID productId,
        String sku,
        String productName,
        int quantityOnHand,
        int quantityReserved,
        int availableQuantity,
        int lowStockThreshold,
        boolean lowStock,
        long inventoryVersion
) {
}

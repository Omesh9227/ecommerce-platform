package com.ecommerce.inventoryservice.event;

import com.ecommerce.inventoryservice.entity.InventoryItem;

import java.time.Instant;
import java.util.UUID;

/**
 * Published on inventory-events. Used for both INVENTORY_UPDATED and INVENTORY_LOW.
 * inventoryVersion increases with every stock change, so consumers can ignore out-of-date events.
 */
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

    public static InventoryEvent of(String eventType, InventoryItem item) {
        return new InventoryEvent(
                UUID.randomUUID(),
                eventType,
                Instant.now(),
                item.getProductId(),
                item.getSku(),
                item.getProductName(),
                item.getQuantityOnHand(),
                item.getQuantityReserved(),
                item.getAvailableQuantity(),
                item.getLowStockThreshold(),
                item.isLowStock(),
                item.getVersion() == null ? 0 : item.getVersion()
        );
    }
}

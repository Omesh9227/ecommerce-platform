package com.ecommerce.inventoryservice.dto.response;

import java.time.Instant;
import java.util.UUID;

public record InventoryItemResponse(
        UUID productId,
        String sku,
        String productName,
        String productStatus,
        int quantityOnHand,
        int quantityReserved,
        int availableQuantity,
        int lowStockThreshold,
        boolean lowStock,
        Instant updatedAt
) {
}

package com.ecommerce.adminservice.dto.response;

import java.time.Instant;
import java.util.UUID;

/** A product at or below its low-stock threshold. */
public record LowStockItem(UUID productId, String sku, String name, int available, int onHand, int reserved, int threshold,
                           boolean outOfStock, Instant updatedAt) {
}

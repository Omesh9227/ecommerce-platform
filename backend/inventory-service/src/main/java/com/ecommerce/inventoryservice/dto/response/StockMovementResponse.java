package com.ecommerce.inventoryservice.dto.response;

import com.ecommerce.inventoryservice.entity.MovementType;

import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
        UUID id,
        MovementType movementType,
        int quantityChange,
        int quantityOnHandAfter,
        int quantityReservedAfter,
        String reason,
        String reference,
        UUID actorId,
        Instant createdAt
) {
}

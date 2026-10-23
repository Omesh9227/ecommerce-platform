package com.ecommerce.inventoryservice.dto.response;

import com.ecommerce.inventoryservice.entity.ReservationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID reservationId,
        String orderReference,
        ReservationStatus status,
        List<Item> items,
        Instant createdAt,
        Instant updatedAt
) {

    public record Item(UUID productId, int quantity) {
    }
}

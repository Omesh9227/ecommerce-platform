package com.ecommerce.orderservice.dto.response;

import com.ecommerce.orderservice.entity.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/** One status change. changedBy is null when the system made the change. */
public record TimelineEntryResponse(
        OrderStatus fromStatus,
        OrderStatus toStatus,
        UUID changedBy,
        String note,
        Instant changedAt
) {
}

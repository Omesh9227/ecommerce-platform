package com.ecommerce.orderservice.event;

import com.ecommerce.orderservice.entity.CustomerOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published on order-events (key = orderId) for payment-service and notification-service.
 * orderVersion increases with every change, so consumers can ignore out-of-date or repeated events.
 */
public record OrderEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID orderId,
        String orderNumber,
        UUID userId,
        String userEmail,
        String status,
        String reason,
        String carrier,
        String trackingNumber,
        BigDecimal totalAmount,
        String currency,
        List<Item> items,
        long orderVersion
) {

    public record Item(UUID productId, String sku, String productName, int quantity, BigDecimal unitPrice) {
    }

    public static OrderEvent of(String eventType, CustomerOrder order, Instant now) {
        return new OrderEvent(
                UUID.randomUUID(),
                eventType,
                now,
                order.getId(),
                order.getOrderNumber(),
                order.getUserId(),
                order.getUserEmail(),
                order.getStatus().name(),
                order.getStatusReason(),
                order.getCarrier(),
                order.getTrackingNumber(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getItems().stream()
                        .map(i -> new Item(i.getProductId(), i.getSku(), i.getProductName(), i.getQuantity(), i.getUnitPrice()))
                        .toList(),
                order.getVersion() == null ? 0 : order.getVersion());
    }
}

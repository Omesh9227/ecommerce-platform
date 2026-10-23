package com.ecommerce.orderservice.dto.response;

import com.ecommerce.orderservice.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param statusReason  why it was cancelled or why checkout failed
 * @param paymentDueAt  while PENDING_PAYMENT: after this the order is cancelled automatically
 * @param timeline      every status change, oldest first
 */
public record OrderResponse(
        UUID orderId,
        String orderNumber,
        UUID userId,
        OrderStatus status,
        String statusReason,
        List<OrderItemResponse> items,
        int itemCount,
        BigDecimal subtotal,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        String currency,
        ShippingAddressResponse shippingAddress,
        Instant paymentDueAt,
        String carrier,
        String trackingNumber,
        Instant placedAt,
        Instant confirmedAt,
        Instant shippedAt,
        Instant deliveredAt,
        Instant cancelledAt,
        Instant createdAt,
        List<TimelineEntryResponse> timeline
) {
}

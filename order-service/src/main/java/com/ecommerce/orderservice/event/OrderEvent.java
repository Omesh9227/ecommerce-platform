package com.ecommerce.orderservice.event;

import com.ecommerce.orderservice.entity.CustomerOrder;
import com.ecommerce.orderservice.entity.ShippingAddress;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Published on order-events (key = orderId) for payment-, notification-, review- and shipping-service.
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
        LocalDate estimatedDeliveryDate,
        BigDecimal totalAmount,
        String currency,
        List<Item> items,
        Address shippingAddress,
        long orderVersion
) {

    public record Item(UUID productId, String sku, String productName, int quantity, BigDecimal unitPrice) {
    }

    /** Where the order goes (shipping-service creates the shipment from ORDER_CONFIRMED). */
    public record Address(String fullName, String phone, String line1, String line2, String city, String state,
                          String postalCode, String country) {

        static Address of(ShippingAddress a) {
            return a == null ? null : new Address(a.getFullName(), a.getPhone(), a.getLine1(), a.getLine2(), a.getCity(),
                    a.getState(), a.getPostalCode(), a.getCountry());
        }
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
                order.getEstimatedDeliveryDate(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getItems().stream()
                        .map(i -> new Item(i.getProductId(), i.getSku(), i.getProductName(), i.getQuantity(), i.getUnitPrice()))
                        .toList(),
                Address.of(order.getShippingAddress()),
                order.getVersion() == null ? 0 : order.getVersion());
    }
}

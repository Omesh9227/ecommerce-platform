package com.ecommerce.orderservice.event;

import com.ecommerce.orderservice.entity.OrderStatus;

import java.util.Optional;

/** Event types: order-events (published here), payment-events and shipment-events (consumed). Unknown types are ignored. */
public final class EventTypes {

    public static final String ORDER_PLACED = "ORDER_PLACED";
    public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";
    public static final String ORDER_SHIPPED = "ORDER_SHIPPED";
    public static final String ORDER_DELIVERED = "ORDER_DELIVERED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

    // Consumed from payment-events (published by payment-service)
    public static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYMENT_REFUNDED = "PAYMENT_REFUNDED";

    // Consumed from shipment-events (published by shipping-service)
    public static final String SHIPMENT_SHIPPED = "SHIPMENT_SHIPPED";
    public static final String SHIPMENT_OUT_FOR_DELIVERY = "SHIPMENT_OUT_FOR_DELIVERY";
    public static final String SHIPMENT_DELIVERY_FAILED = "SHIPMENT_DELIVERY_FAILED";
    public static final String SHIPMENT_DELIVERED = "SHIPMENT_DELIVERED";

    private EventTypes() {
    }

    /** The event announcing that an order reached this status (internal statuses have none). */
    public static Optional<String> forStatus(OrderStatus status) {
        return Optional.ofNullable(switch (status) {
            case PENDING_PAYMENT -> ORDER_PLACED;
            case CONFIRMED -> ORDER_CONFIRMED;
            case SHIPPED -> ORDER_SHIPPED;
            case DELIVERED -> ORDER_DELIVERED;
            case CANCELLED -> ORDER_CANCELLED;
            case CREATED, CHECKOUT_FAILED -> null;
        });
    }
}

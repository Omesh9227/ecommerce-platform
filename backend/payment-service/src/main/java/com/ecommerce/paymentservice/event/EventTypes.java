package com.ecommerce.paymentservice.event;

/** Event type names on the wire. Unknown types are ignored by consumers. */
public final class EventTypes {

    // Consumed from order-events (published by order-service)
    public static final String ORDER_PLACED = "ORDER_PLACED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

    // Published on payment-events
    public static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYMENT_REFUNDED = "PAYMENT_REFUNDED";

    private EventTypes() {
    }
}

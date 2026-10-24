package com.ecommerce.notificationservice.event;

/** Event types this service reacts to. Anything else is ignored. */
public final class EventTypes {

    // order-events (order-service)
    public static final String ORDER_PLACED = "ORDER_PLACED";
    public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";
    public static final String ORDER_SHIPPED = "ORDER_SHIPPED";
    public static final String ORDER_DELIVERED = "ORDER_DELIVERED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

    // payment-events (payment-service)
    public static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYMENT_REFUNDED = "PAYMENT_REFUNDED";

    // shipment-events (shipping-service). Shipped and delivered emails come from the order's own events.
    public static final String SHIPMENT_OUT_FOR_DELIVERY = "SHIPMENT_OUT_FOR_DELIVERY";
    public static final String SHIPMENT_DELIVERY_FAILED = "SHIPMENT_DELIVERY_FAILED";

    // wishlist-events (wishlist-service)
    public static final String WISHLIST_PRICE_DROPPED = "WISHLIST_PRICE_DROPPED";

    private EventTypes() {
    }
}

package com.ecommerce.adminservice.event;

import java.util.Set;

/** Event types the dashboard records. Anything else is ignored. */
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

    // inventory-events (inventory-service)
    public static final String INVENTORY_UPDATED = "INVENTORY_UPDATED";
    public static final String INVENTORY_LOW = "INVENTORY_LOW";

    // shipment-events (shipping-service)
    public static final String SHIPMENT_DELIVERY_FAILED = "SHIPMENT_DELIVERY_FAILED";

    public static final Set<String> ORDER = Set.of(ORDER_PLACED, ORDER_CONFIRMED, ORDER_SHIPPED, ORDER_DELIVERED, ORDER_CANCELLED);
    public static final Set<String> PAYMENT = Set.of(PAYMENT_SUCCEEDED, PAYMENT_FAILED, PAYMENT_REFUNDED);
    public static final Set<String> STOCK = Set.of(INVENTORY_UPDATED, INVENTORY_LOW);

    private EventTypes() {
    }
}

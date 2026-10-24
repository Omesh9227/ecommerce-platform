package com.ecommerce.shippingservice.event;

import com.ecommerce.shippingservice.entity.ShipmentStatus;

import java.util.Optional;

/** Event types consumed (order-events) and published (shipment-events). Unknown types are ignored. */
public final class EventTypes {

    // Consumed from order-events (order-service)
    public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

    // Published on shipment-events
    public static final String SHIPMENT_SHIPPED = "SHIPMENT_SHIPPED";
    public static final String SHIPMENT_OUT_FOR_DELIVERY = "SHIPMENT_OUT_FOR_DELIVERY";
    public static final String SHIPMENT_DELIVERY_FAILED = "SHIPMENT_DELIVERY_FAILED";
    public static final String SHIPMENT_DELIVERED = "SHIPMENT_DELIVERED";

    private EventTypes() {
    }

    /**
     * The event announcing that a shipment reached this status. Warehouse steps (PENDING, PACKED), hub scans
     * (IN_TRANSIT) and cancellations (which come from the order itself) are not announced.
     */
    public static Optional<String> forStatus(ShipmentStatus status) {
        return Optional.ofNullable(switch (status) {
            case SHIPPED -> SHIPMENT_SHIPPED;
            case OUT_FOR_DELIVERY -> SHIPMENT_OUT_FOR_DELIVERY;
            case DELIVERY_FAILED -> SHIPMENT_DELIVERY_FAILED;
            case DELIVERED -> SHIPMENT_DELIVERED;
            case PENDING, PACKED, IN_TRANSIT, CANCELLED -> null;
        });
    }
}

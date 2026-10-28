package com.ecommerce.shippingservice.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * A shipment's journey:
 * <pre>
 * PENDING ──► PACKED ──► SHIPPED ──► IN_TRANSIT ──► OUT_FOR_DELIVERY ──► DELIVERED
 *    │           │                    (each hub scan)       │      ▲
 *    ▼           ▼                                          ▼      │ next attempt
 * CANCELLED ◄────┘  (the order was cancelled)          DELIVERY_FAILED
 * </pre>
 * PENDING and PACKED happen in our warehouse; from SHIPPED on, the parcel is with the carrier, whose scans are
 * recorded as tracking updates. A shipment can't be cancelled once it has left the warehouse.
 */
public enum ShipmentStatus {
    PENDING("Order confirmed - preparing your package"),
    PACKED("Packed and ready to ship"),
    SHIPPED("Shipped"),
    IN_TRANSIT("In transit"),
    OUT_FOR_DELIVERY("Out for delivery"),
    DELIVERY_FAILED("Delivery attempt failed"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled");

    /** Statuses a carrier scan (tracking update) may report. */
    public static final Set<ShipmentStatus> CARRIER_UPDATES = EnumSet.of(IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERY_FAILED, DELIVERED);

    private final String description;

    ShipmentStatus(String description) {
        this.description = description;
    }

    /** Default text for the tracking history. */
    public String description() {
        return description;
    }

    public boolean canMoveTo(ShipmentStatus next) {
        return switch (this) {
            case PENDING -> next == PACKED || next == CANCELLED;
            case PACKED -> next == SHIPPED || next == CANCELLED;
            case SHIPPED -> next == IN_TRANSIT || next == OUT_FOR_DELIVERY || next == DELIVERED;
            // IN_TRANSIT -> IN_TRANSIT: the parcel reached another hub
            case IN_TRANSIT -> next == IN_TRANSIT || next == OUT_FOR_DELIVERY || next == DELIVERED;
            case OUT_FOR_DELIVERY -> next == DELIVERED || next == DELIVERY_FAILED;
            // after a failed attempt: tried again, or back at the hub
            case DELIVERY_FAILED -> next == OUT_FOR_DELIVERY || next == IN_TRANSIT;
            case DELIVERED, CANCELLED -> false;
        };
    }

    /** Still in our warehouse (not handed to a carrier yet). */
    public boolean isInWarehouse() {
        return this == PENDING || this == PACKED;
    }

    /** Has been packed (now or earlier) and not cancelled. */
    public boolean isPacked() {
        return this != PENDING && this != CANCELLED;
    }

    /** Has left the warehouse with a carrier. */
    public boolean hasShipped() {
        return !isInWarehouse() && this != CANCELLED;
    }
}

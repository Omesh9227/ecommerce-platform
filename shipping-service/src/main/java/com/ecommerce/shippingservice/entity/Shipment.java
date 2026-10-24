package com.ecommerce.shippingservice.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The parcel for one paid order. Created from order-service's ORDER_CONFIRMED event; moved along by the warehouse
 * (pack, ship) and the carrier's scans. Status changes go through {@link #moveTo}, which refuses moves the
 * lifecycle doesn't allow (see {@link ShipmentStatus}).
 */
@Entity
@Table(name = "shipments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "order_number", nullable = false, updatable = false, length = 30)
    private String orderNumber;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "user_email", length = 255, updatable = false)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ShipmentStatus status;

    /** Why it was cancelled, or why the last delivery attempt failed. */
    @Column(name = "status_reason", length = 300)
    private String statusReason;

    @Column(name = "carrier", length = 50)
    private String carrier;

    @Column(name = "tracking_number", length = 40)
    private String trackingNumber;

    @Embedded
    private DeliveryAddress address;

    @Column(name = "estimated_delivery_date", nullable = false)
    private LocalDate estimatedDeliveryDate;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    /** orderVersion of the order event that created it. */
    @Column(name = "order_version", nullable = false, updatable = false)
    private long orderVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "packed_at")
    private Instant packedAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Increases with every change; sent as shipmentVersion so consumers can skip old or repeated events. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productName ASC")
    private List<ShipmentItem> items = new ArrayList<>();

    public static Shipment create(UUID orderId, String orderNumber, UUID userId, String userEmail, DeliveryAddress address,
                                  LocalDate estimatedDeliveryDate, long orderVersion, Instant now) {
        Shipment shipment = new Shipment();
        shipment.id = UUID.randomUUID();
        shipment.orderId = orderId;
        shipment.orderNumber = orderNumber;
        shipment.userId = userId;
        shipment.userEmail = userEmail;
        shipment.address = address;
        shipment.status = ShipmentStatus.PENDING;
        shipment.estimatedDeliveryDate = estimatedDeliveryDate;
        shipment.orderVersion = orderVersion;
        shipment.createdAt = now;
        shipment.updatedAt = now;
        return shipment;
    }

    public void addItem(UUID productId, String sku, String productName, int quantity) {
        items.add(new ShipmentItem(this, productId, sku, productName, quantity));
    }

    /**
     * @param reason kept as statusReason for CANCELLED and DELIVERY_FAILED (cleared by the next move)
     * @throws IllegalStateException if the lifecycle doesn't allow it (callers check {@link ShipmentStatus#canMoveTo} first)
     */
    public void moveTo(ShipmentStatus next, String reason, Instant now) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Shipment for " + orderNumber + " can't go from " + status + " to " + next);
        }
        status = next;
        statusReason = next == ShipmentStatus.CANCELLED || next == ShipmentStatus.DELIVERY_FAILED ? reason : null;
        switch (next) {
            case PACKED -> packedAt = now;
            case SHIPPED -> shippedAt = now;
            case DELIVERED -> deliveredAt = now;
            case CANCELLED -> cancelledAt = now;
            case DELIVERY_FAILED -> failedAttempts++;
            default -> {
            }
        }
        updatedAt = now;
    }

    /** Hands the parcel to a carrier (PACKED -> SHIPPED) with a new delivery estimate counted from today. */
    public void ship(String carrier, String trackingNumber, LocalDate estimatedDeliveryDate, Instant now) {
        moveTo(ShipmentStatus.SHIPPED, null, now);
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.estimatedDeliveryDate = estimatedDeliveryDate;
    }

    /** Records a change that isn't a status move (e.g. a note on a delivered parcel), so the version still changes. */
    public void touch(Instant now) {
        updatedAt = now;
    }

    public long currentVersion() {
        return version == null ? 0 : version;
    }
}

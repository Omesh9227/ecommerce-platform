package com.ecommerce.shippingservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One line of a shipment's tracking history ("In transit - Hyderabad hub - 24 Oct, 14:05"). Never changed. */
@Entity
@Table(name = "tracking_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Set by the database (identity): the order lines were recorded in. */
    @Column(name = "seq", insertable = false, updatable = false)
    private Long seq;

    @Column(name = "shipment_id", nullable = false, updatable = false)
    private UUID shipmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 30)
    private ShipmentStatus status;

    @Column(name = "location", length = 100, updatable = false)
    private String location;

    @Column(name = "description", nullable = false, length = 300, updatable = false)
    private String description;

    /** The admin who recorded it; null for the system (e.g. created from an order event). */
    @Column(name = "recorded_by", updatable = false)
    private UUID recordedBy;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public TrackingEvent(UUID shipmentId, ShipmentStatus status, String location, String description, UUID recordedBy,
                         Instant occurredAt) {
        this.shipmentId = shipmentId;
        this.status = status;
        this.location = location;
        this.description = description;
        this.recordedBy = recordedBy;
        this.occurredAt = occurredAt;
    }
}

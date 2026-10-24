package com.ecommerce.shippingservice.service;

import com.ecommerce.shippingservice.config.ShippingProperties;
import com.ecommerce.shippingservice.dto.request.ShipRequest;
import com.ecommerce.shippingservice.dto.request.TrackingUpdateRequest;
import com.ecommerce.shippingservice.dto.response.ShipmentResponse;
import com.ecommerce.shippingservice.entity.Shipment;
import com.ecommerce.shippingservice.entity.ShipmentStatus;
import com.ecommerce.shippingservice.entity.TrackingEvent;
import com.ecommerce.shippingservice.exception.BadRequestException;
import com.ecommerce.shippingservice.exception.ConflictException;
import com.ecommerce.shippingservice.exception.ResourceNotFoundException;
import com.ecommerce.shippingservice.repository.ShipmentRepository;
import com.ecommerce.shippingservice.repository.TrackingEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Warehouse and carrier updates (ADMIN). Each one locks the shipment row for its transaction, so two updates (or an
 * update and a cancellation from order-service) never overlap. Repeating an update that already happened returns
 * the shipment unchanged (safe retries); an update the lifecycle doesn't allow is a 409.
 */
@Service
@RequiredArgsConstructor
public class ShipmentCommands {

    private final ShipmentRepository shipmentRepository;
    private final TrackingEventRepository trackingEventRepository;
    private final ShipmentTransitions transitions;
    private final DeliveryEstimator estimator;
    private final TrackingNumbers trackingNumbers;
    private final ShippingProperties properties;
    private final Clock clock;

    /** PENDING -> PACKED. */
    @Transactional
    public ShipmentResponse pack(UUID shipmentId, UUID adminId) {
        Shipment shipment = lock(shipmentId);
        if (shipment.getStatus() == ShipmentStatus.CANCELLED) {
            throw cancelled(shipment);
        }
        if (!shipment.getStatus().isPacked()) {
            transitions.apply(shipment, ShipmentStatus.PACKED, null, null, ShipmentStatus.PACKED.description(),
                    adminId, clock.instant());
        }
        return response(shipment);
    }

    /** PACKED -> SHIPPED: handed to a carrier, with its tracking number (or ours) and a fresh delivery estimate. */
    @Transactional
    public ShipmentResponse ship(UUID shipmentId, ShipRequest request, UUID adminId) {
        String carrier = properties.carrier(request.carrier()).orElseThrow(() -> new BadRequestException(
                "Unknown carrier '" + request.carrier().trim() + "'. Carriers: " + String.join(", ", properties.carriers())));
        String requestedNumber = TrackingNumbers.normalize(request.trackingNumber());
        Shipment shipment = lock(shipmentId);

        if (shipment.getStatus().hasShipped()) {
            boolean sameShipping = carrier.equals(shipment.getCarrier())
                    && (requestedNumber == null || requestedNumber.equals(shipment.getTrackingNumber()));
            if (sameShipping) {
                return response(shipment); // repeated request
            }
            throw new ConflictException("Shipment for " + shipment.getOrderNumber() + " was already shipped with "
                    + shipment.getCarrier() + " (" + shipment.getTrackingNumber() + ")");
        }
        switch (shipment.getStatus()) {
            case CANCELLED -> throw cancelled(shipment);
            case PENDING -> throw new ConflictException("Shipment for " + shipment.getOrderNumber()
                    + " isn't packed yet - pack it first");
            default -> {
            }
        }
        if (requestedNumber != null && shipmentRepository.existsByTrackingNumber(requestedNumber)) {
            throw new ConflictException("Tracking number " + requestedNumber + " is already used by another shipment");
        }
        String trackingNumber = requestedNumber != null ? requestedNumber : trackingNumbers.generate();
        Instant now = clock.instant();
        shipment.ship(carrier, trackingNumber, estimator.estimate(shipment.getAddress().getPostalCode(), now), now);
        transitions.recorded(shipment, trim(request.location()),
                "Shipped with " + carrier + " - tracking number " + trackingNumber, null, adminId, now);
        return response(shipment);
    }

    /** A carrier scan: IN_TRANSIT (at a hub), OUT_FOR_DELIVERY, DELIVERY_FAILED (with the reason) or DELIVERED. */
    @Transactional
    public ShipmentResponse update(UUID shipmentId, TrackingUpdateRequest request, UUID adminId) {
        ShipmentStatus next = request.status();
        String location = trim(request.location());
        String note = trim(request.note());
        if (!ShipmentStatus.CARRIER_UPDATES.contains(next)) {
            throw new BadRequestException("status must be one of " + ShipmentStatus.CARRIER_UPDATES.stream()
                    .map(Enum::name).collect(Collectors.joining(", ")) + " (packing and shipping have their own endpoints)");
        }
        if (next == ShipmentStatus.IN_TRANSIT && location == null) {
            throw new BadRequestException("location is required for IN_TRANSIT (which hub is the parcel at?)");
        }
        if (next == ShipmentStatus.DELIVERY_FAILED && note == null) {
            throw new BadRequestException("note is required for DELIVERY_FAILED (why couldn't it be delivered?)");
        }

        Shipment shipment = lock(shipmentId);
        if (isRepeat(shipment, next, location)) {
            return response(shipment);
        }
        if (!shipment.getStatus().canMoveTo(next)) {
            String hint = shipment.getStatus().isInWarehouse() ? " (it hasn't been shipped yet)" : "";
            throw new ConflictException("Shipment for " + shipment.getOrderNumber() + " is " + shipment.getStatus()
                    + " and can't be marked " + next + hint);
        }
        String description = switch (next) {
            case DELIVERY_FAILED -> "Delivery attempt failed: " + note;
            case DELIVERED -> note == null ? "Delivered" : "Delivered - " + note;
            default -> note == null ? next.description() : next.description() + " - " + note;
        };
        transitions.apply(shipment, next, note, location, description, adminId, clock.instant());
        return response(shipment);
    }

    // ---------- Helpers ----------

    /** The same scan again (a carrier retrying): same status, and for IN_TRANSIT the same hub. */
    private boolean isRepeat(Shipment shipment, ShipmentStatus next, String location) {
        if (shipment.getStatus() != next) {
            return false;
        }
        if (next != ShipmentStatus.IN_TRANSIT) {
            return true;
        }
        return trackingEventRepository.findFirstByShipmentIdOrderByOccurredAtDescSeqDesc(shipment.getId())
                .map(TrackingEvent::getLocation)
                .map(last -> last.equalsIgnoreCase(Objects.requireNonNullElse(location, "")))
                .orElse(false);
    }

    private Shipment lock(UUID shipmentId) {
        return shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", shipmentId));
    }

    private ShipmentResponse response(Shipment shipment) {
        return ShipmentResponse.of(shipment, trackingEventRepository.findByShipmentIdOrderByOccurredAtAscSeqAsc(shipment.getId()), true);
    }

    private static ConflictException cancelled(Shipment shipment) {
        return new ConflictException("Shipment for " + shipment.getOrderNumber() + " was cancelled: "
                + shipment.getStatusReason());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

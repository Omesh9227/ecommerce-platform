package com.ecommerce.shippingservice.service;

import com.ecommerce.shippingservice.entity.DeliveryAddress;
import com.ecommerce.shippingservice.entity.Shipment;
import com.ecommerce.shippingservice.entity.ShipmentStatus;
import com.ecommerce.shippingservice.event.EventTypes;
import com.ecommerce.shippingservice.event.OrderEvent;
import com.ecommerce.shippingservice.repository.ShipmentRepository;
import com.ecommerce.shippingservice.repository.TrackingEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Reacts to order-service:
 * - ORDER_CONFIRMED (the order is paid): a shipment is created, waiting to be packed, with a delivery estimate;
 * - ORDER_CANCELLED: a shipment still in the warehouse is cancelled. If it already left (the customer cancelled at
 *   the very moment it was shipped), the tracking history says so, for the warehouse to recall it from the carrier.
 * Other events are ignored. Every event is safe to receive twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventHandler {

    static final String RECALL_NOTE = "The order was cancelled after the parcel left the warehouse - recall it from the carrier";

    private final ShipmentRepository shipmentRepository;
    private final TrackingEventRepository trackingEventRepository;
    private final ShipmentTransitions transitions;
    private final DeliveryEstimator estimator;
    private final Clock clock;

    @Transactional
    public void onOrderEvent(OrderEvent event) {
        if (event.eventType() == null) {
            throw new IllegalArgumentException("Order event " + event.eventId() + " has no type");
        }
        switch (event.eventType()) {
            case EventTypes.ORDER_CONFIRMED -> createShipment(event);
            case EventTypes.ORDER_CANCELLED -> cancelShipment(event);
            default -> log.debug("Ignoring order event {} for {}", event.eventType(), event.orderNumber());
        }
    }

    private void createShipment(OrderEvent event) {
        if (event.orderId() == null || event.orderNumber() == null || event.userId() == null) {
            throw new IllegalArgumentException("Order event " + event.eventId() + " is missing required fields");
        }
        if (event.shippingAddress() == null) {
            // Orders paid before shipping-service existed: their events carry no address. Nothing to ship from here.
            log.warn("Order {} was confirmed without a shipping address (an event from before shipping-service); skipped",
                    event.orderNumber());
            return;
        }
        if (shipmentRepository.existsByOrderId(event.orderId())) {
            log.debug("Shipment for {} already exists", event.orderNumber());
            return;
        }
        if (event.items() == null || event.items().isEmpty()) {
            throw new IllegalArgumentException("Order " + event.orderNumber() + " has no items");
        }
        DeliveryAddress address = toAddress(event);
        Instant now = clock.instant();
        Shipment shipment = Shipment.create(event.orderId(), event.orderNumber(), event.userId(), event.userEmail(),
                address, estimator.estimate(address.getPostalCode(), now), event.orderVersion(), now);
        event.items().stream().filter(Objects::nonNull).forEach(item -> {
            if (item.productId() == null || item.quantity() <= 0) {
                throw new IllegalArgumentException("Order " + event.orderNumber() + " has an invalid item");
            }
            shipment.addItem(item.productId(), item.sku(), item.productName(), item.quantity());
        });
        transitions.recorded(shipment, null, ShipmentStatus.PENDING.description(), null, null, now);
        log.info("Shipment created for order {} to {} {}, estimated delivery {}", event.orderNumber(),
                address.getCity(), address.getPostalCode(), shipment.getEstimatedDeliveryDate());
    }

    private void cancelShipment(OrderEvent event) {
        if (event.orderId() == null) {
            throw new IllegalArgumentException("Order event " + event.eventId() + " has no orderId");
        }
        Shipment shipment = shipmentRepository.findByOrderIdForUpdate(event.orderId()).orElse(null);
        if (shipment == null) {
            log.debug("Order {} cancelled before it was paid: no shipment", event.orderNumber());
            return;
        }
        ShipmentStatus status = shipment.getStatus();
        Instant now = clock.instant();
        if (status.isInWarehouse()) {
            String reason = event.reason() == null || event.reason().isBlank() ? "The order was cancelled" : event.reason().trim();
            transitions.apply(shipment, ShipmentStatus.CANCELLED, reason, null, "Cancelled: " + reason, null, now);
        } else if (status != ShipmentStatus.CANCELLED && !alreadyNoted(shipment)) {
            log.warn("Order {} was cancelled but its shipment is already {}: recall needed", shipment.getOrderNumber(), status);
            transitions.note(shipment, null, RECALL_NOTE, null, now);
        }
    }

    private boolean alreadyNoted(Shipment shipment) {
        return trackingEventRepository.findByShipmentIdOrderByOccurredAtAscSeqAsc(shipment.getId()).stream()
                .anyMatch(e -> RECALL_NOTE.equals(e.getDescription()));
    }

    private static DeliveryAddress toAddress(OrderEvent event) {
        OrderEvent.Address a = event.shippingAddress();
        if (blank(a.fullName()) || blank(a.phone()) || blank(a.line1()) || blank(a.city()) || blank(a.state())
                || a.postalCode() == null || !DeliveryEstimator.PIN_CODE.matcher(a.postalCode()).matches()) {
            throw new IllegalArgumentException("Order " + event.orderNumber() + " has an incomplete shipping address");
        }
        String country = blank(a.country()) ? "IN" : a.country().trim().toUpperCase(Locale.ROOT);
        return new DeliveryAddress(a.fullName().trim(), a.phone().trim(), a.line1().trim(),
                blank(a.line2()) ? null : a.line2().trim(), a.city().trim(), a.state().trim(), a.postalCode(), country);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

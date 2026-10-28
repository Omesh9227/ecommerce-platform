package com.ecommerce.notificationservice.service;

import com.ecommerce.notificationservice.entity.Notification;
import com.ecommerce.notificationservice.entity.OrderSnapshot;
import com.ecommerce.notificationservice.event.OrderEvent;
import com.ecommerce.notificationservice.event.PaymentEvent;
import com.ecommerce.notificationservice.event.ShipmentEvent;
import com.ecommerce.notificationservice.event.WishlistEvent;
import com.ecommerce.notificationservice.mail.EmailComposer;
import com.ecommerce.notificationservice.mail.EmailContent;
import com.ecommerce.notificationservice.mail.EmailSender;
import com.ecommerce.notificationservice.repository.NotificationRepository;
import com.ecommerce.notificationservice.repository.OrderSnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns events into emails, at most one per event:
 * the eventId is recorded in the same transaction as the send, and an event seen before is skipped.
 * If sending fails, the transaction rolls back and Kafka retries the event, so no email is lost.
 * (At-least-once: in the rare case the mail server accepted the email but the database commit then failed,
 * the customer could get the same email twice - never zero.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final TypeReference<List<OrderEvent.Item>> ITEMS = new TypeReference<>() {
    };

    private final OrderSnapshotRepository snapshotRepository;
    private final NotificationRepository notificationRepository;
    private final EmailComposer composer;
    private final EmailSender emailSender;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public void onOrderEvent(OrderEvent event) {
        if (event.eventId() == null || event.eventType() == null || event.orderId() == null || event.userId() == null) {
            throw new IllegalArgumentException("Order event " + event.eventId() + " is missing required fields");
        }
        rememberOrder(event);

        if (notificationRepository.existsByEventId(event.eventId())) {
            return; // already emailed (event delivered twice)
        }
        composer.forOrderEvent(event).ifPresent(content ->
                send(event.eventId(), event.eventType(), event.orderId(), event.orderNumber(), event.userId(),
                        event.userEmail(), content));
    }

    @Transactional
    public void onPaymentEvent(PaymentEvent event) {
        if (event.eventId() == null || event.eventType() == null || event.orderId() == null) {
            throw new IllegalArgumentException("Payment event " + event.eventId() + " is missing required fields");
        }
        if (!EmailComposer.emailsFor(event) || notificationRepository.existsByEventId(event.eventId())) {
            return;
        }
        OrderSnapshot order = snapshotRepository.findById(event.orderId())
                .orElseThrow(() -> new OrderNotKnownYetException(event.orderNumber()));

        composer.forPaymentEvent(event, readItems(order)).ifPresent(content ->
                send(event.eventId(), event.eventType(), order.getOrderId(), order.getOrderNumber(), order.getUserId(),
                        order.getEmail(), content));
    }

    @Transactional
    public void onWishlistEvent(WishlistEvent event) {
        if (event.eventId() == null || event.eventType() == null || event.userId() == null) {
            throw new IllegalArgumentException("Wishlist event " + event.eventId() + " is missing required fields");
        }
        if (notificationRepository.existsByEventId(event.eventId())) {
            return;
        }
        composer.forWishlistEvent(event).ifPresent(content ->
                send(event.eventId(), event.eventType(), null, null, event.userId(), event.userEmail(), content));
    }

    @Transactional
    public void onShipmentEvent(ShipmentEvent event) {
        if (event.eventId() == null || event.eventType() == null || event.orderId() == null || event.userId() == null) {
            throw new IllegalArgumentException("Shipment event " + event.eventId() + " is missing required fields");
        }
        if (notificationRepository.existsByEventId(event.eventId())) {
            return;
        }
        composer.forShipmentEvent(event).ifPresent(content ->
                send(event.eventId(), event.eventType(), event.orderId(), event.orderNumber(), event.userId(),
                        event.userEmail(), content));
    }

    // ---------- Helpers ----------

    private void rememberOrder(OrderEvent event) {
        OrderSnapshot snapshot = snapshotRepository.findById(event.orderId()).orElseGet(() -> new OrderSnapshot(event.orderId()));
        if (snapshot.update(event.orderNumber(), event.userId(), event.userEmail(), event.totalAmount(), event.currency(),
                writeItems(event.items()), event.orderVersion(), clock.instant())) {
            snapshotRepository.save(snapshot);
        }
    }

    private void send(UUID eventId, String eventType, UUID orderId, String orderNumber, UUID userId, String recipient,
                      EmailContent content) {
        String about = orderNumber != null ? "order " + orderNumber : "user " + userId;
        if (recipient == null || recipient.isBlank()) {
            log.warn("No email address for {}; {} not sent", about, eventType);
            return;
        }
        emailSender.send(recipient, content);
        Instant now = clock.instant();
        notificationRepository.save(Notification.emailSent(eventId, eventType, orderId, orderNumber, userId, recipient,
                content.subject(), now));
        log.info("Email sent: {} for {} to {}", eventType, about, mask(recipient));
    }

    private String writeItems(List<OrderEvent.Item> items) {
        try {
            return objectMapper.writeValueAsString(items == null ? List.of() : items);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not store order items", e);
        }
    }

    private List<OrderEvent.Item> readItems(OrderSnapshot order) {
        try {
            return objectMapper.readValue(order.getItemsJson(), ITEMS);
        } catch (JsonProcessingException e) {
            log.warn("Unreadable items for order {}: {}", order.getOrderNumber(), e.getOriginalMessage());
            return List.of();
        }
    }

    /** omesh@example.com -> om***@example.com (logs never hold full addresses). */
    static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 2 ? "***" + email.substring(Math.max(at, 0)) : email.substring(0, 2) + "***" + email.substring(at);
    }

    /** Exposed for the read API. */
    public Optional<OrderSnapshot> snapshot(UUID orderId) {
        return snapshotRepository.findById(orderId);
    }
}

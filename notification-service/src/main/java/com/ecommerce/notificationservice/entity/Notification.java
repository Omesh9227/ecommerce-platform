package com.ecommerce.notificationservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One email that was sent (saved in the same transaction that sent it). */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    public static final String CHANNEL_EMAIL = "EMAIL";
    public static final String STATUS_SENT = "SENT";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 50)
    private String eventType;

    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @Column(name = "order_number", updatable = false, length = 20)
    private String orderNumber;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "channel", nullable = false, updatable = false, length = 10)
    private String channel;

    @Column(name = "recipient", nullable = false, updatable = false)
    private String recipient;

    @Column(name = "subject", nullable = false, updatable = false)
    private String subject;

    @Column(name = "status", nullable = false, updatable = false, length = 10)
    private String status;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    public static Notification emailSent(UUID eventId, String eventType, UUID orderId, String orderNumber, UUID userId,
                                         String recipient, String subject, Instant sentAt) {
        Notification n = new Notification();
        n.eventId = eventId;
        n.eventType = eventType;
        n.orderId = orderId;
        n.orderNumber = orderNumber;
        n.userId = userId;
        n.channel = CHANNEL_EMAIL;
        n.recipient = recipient;
        n.subject = subject;
        n.status = STATUS_SENT;
        n.sentAt = sentAt;
        return n;
    }
}

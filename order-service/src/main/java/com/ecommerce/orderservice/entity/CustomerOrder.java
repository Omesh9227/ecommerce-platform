package com.ecommerce.orderservice.entity;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * An order (table "orders"; the class isn't called Order because ORDER is a keyword in JPQL and SQL).
 * Status changes go through {@link #moveTo}, which enforces the lifecycle in {@link OrderStatus}.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerOrder {

    /** Assigned, not generated: derived from (user, Idempotency-Key) - see {@link #idFor}. */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_number", nullable = false, updatable = false, length = 20)
    private String orderNumber;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "user_email", updatable = false)
    private String userEmail;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    /** Why the order was cancelled or why checkout failed. */
    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "item_count", nullable = false, updatable = false)
    private int itemCount;

    @Column(name = "subtotal", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_fee", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal shippingFee;

    @Column(name = "total_amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Embedded
    private ShippingAddress shippingAddress;

    @Column(name = "payment_due_at")
    private Instant paymentDueAt;

    @Column(name = "carrier", length = 100)
    private String carrier;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "placed_at")
    private Instant placedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productName ASC")
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** The same user + Idempotency-Key always gives the same order id, so a retried checkout finds its order. */
    public static UUID idFor(UUID userId, String idempotencyKey) {
        return UUID.nameUUIDFromBytes((userId + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
    }

    /** Short, readable and derived from the id, e.g. ORD-3FA85F645717. Also the stock reservation reference. */
    public static String orderNumberFor(UUID orderId) {
        return "ORD-" + orderId.toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    /** A new order in status CREATED (stock not reserved yet). */
    public static CustomerOrder create(UUID userId, String userEmail, String idempotencyKey, String currency,
                                       List<OrderItem> items, BigDecimal shippingFee, ShippingAddress address) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one item");
        }
        CustomerOrder order = new CustomerOrder();
        order.id = idFor(userId, idempotencyKey);
        order.orderNumber = orderNumberFor(order.id);
        order.userId = userId;
        order.userEmail = userEmail;
        order.idempotencyKey = idempotencyKey;
        order.status = OrderStatus.CREATED;
        order.currency = currency;
        order.shippingAddress = address;
        items.forEach(item -> {
            item.attachTo(order);
            order.items.add(item);
        });
        order.itemCount = items.stream().mapToInt(OrderItem::getQuantity).sum();
        order.subtotal = items.stream().map(OrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        order.shippingFee = shippingFee;
        order.totalAmount = order.subtotal.add(shippingFee);
        return order;
    }

    /**
     * Moves to {@code next} and records when. Callers check {@link OrderStatus#canMoveTo} first so they can give
     * a helpful error; this is the last line of defence.
     */
    public void moveTo(OrderStatus next, Instant now, String reason) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Order " + orderNumber + " can't go from " + status + " to " + next);
        }
        status = next;
        switch (next) {
            case PENDING_PAYMENT -> placedAt = now;
            case CONFIRMED -> confirmedAt = now;
            case SHIPPED -> shippedAt = now;
            case DELIVERED -> deliveredAt = now;
            case CANCELLED -> {
                cancelledAt = now;
                statusReason = reason;
            }
            case CHECKOUT_FAILED -> statusReason = reason;
            case CREATED -> {
                // never a target
            }
        }
    }

    public void setPaymentDueAt(Instant paymentDueAt) {
        this.paymentDueAt = paymentDueAt;
    }

    public void setShipment(String carrier, String trackingNumber) {
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
    }
}

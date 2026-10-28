package com.ecommerce.notificationservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The latest known state of an order (from order-events), so payment emails know who to write to and what was bought. */
@Entity
@Table(name = "order_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderSnapshot {

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "order_number", nullable = false, length = 20)
    private String orderNumber;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "email")
    private String email;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** The order's items as JSON (see OrderEvent.Item). */
    @Column(name = "items_json", nullable = false, length = 20000)
    private String itemsJson;

    @Column(name = "order_version", nullable = false)
    private long orderVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public OrderSnapshot(UUID orderId) {
        this.orderId = orderId;
        this.orderVersion = -1;
    }

    /** @return false (nothing changed) if this snapshot already has a newer or equal version */
    public boolean update(String orderNumber, UUID userId, String email, BigDecimal totalAmount, String currency,
                          String itemsJson, long orderVersion, Instant now) {
        if (orderVersion <= this.orderVersion) {
            return false;
        }
        this.orderNumber = orderNumber;
        this.userId = userId;
        this.email = email;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.itemsJson = itemsJson;
        this.orderVersion = orderVersion;
        this.updatedAt = now;
        return true;
    }
}

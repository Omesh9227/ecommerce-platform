package com.ecommerce.adminservice.ingest;

import com.ecommerce.adminservice.event.EventTypes;
import com.ecommerce.adminservice.event.InventoryEvent;
import com.ecommerce.adminservice.event.OrderEvent;
import com.ecommerce.adminservice.event.PaymentEvent;
import com.ecommerce.adminservice.event.ShipmentEvent;
import com.ecommerce.adminservice.ingest.ActivityLog.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Turns events into report data and activity-feed lines, each in one transaction. Orders keep their latest state
 * (only a newer orderVersion is applied) and each step's time; payment events and missed deliveries are stored once
 * per event id; stock keeps the newest inventoryVersion. Every event is safe to receive twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventRecorder {

    private final NamedParameterJdbcTemplate jdbc;
    private final ActivityLog activity;
    private final Clock clock;

    // ---------- order-events ----------

    @Transactional
    public boolean apply(OrderEvent e) {
        if (e.eventType() == null || !EventTypes.ORDER.contains(e.eventType())) {
            return false;
        }
        if (e.orderId() == null || e.orderNumber() == null || e.userId() == null || e.status() == null || e.totalAmount() == null) {
            throw new IllegalArgumentException("Order event " + e.eventId() + " is missing required fields");
        }
        Instant at = at(e.occurredAt());
        Map<UUID, OrderEvent.Item> items = new LinkedHashMap<>();
        if (e.items() != null) {
            e.items().stream().filter(Objects::nonNull).filter(i -> i.productId() != null && i.quantity() > 0)
                    .forEach(i -> items.merge(i.productId(), i, (a, b) -> new OrderEvent.Item(a.productId(), a.sku(),
                            a.productName(), a.quantity() + b.quantity(), a.unitPrice())));
        }
        String type = e.eventType();
        int rows = jdbc.update("""
                INSERT INTO orders (order_id, order_number, user_id, status, total_amount, currency, item_count,
                                    placed_at, paid_at, shipped_at, delivered_at, cancelled_at, cancel_reason,
                                    order_version, updated_at)
                VALUES (:id, :number, :userId, :status, :total, :currency, :itemCount,
                        :placedAt, :paidAt, :shippedAt, :deliveredAt, :cancelledAt, :reason, :version, :now)
                ON CONFLICT (order_id) DO UPDATE SET status = EXCLUDED.status, total_amount = EXCLUDED.total_amount,
                    item_count = EXCLUDED.item_count,
                    placed_at = COALESCE(orders.placed_at, EXCLUDED.placed_at),
                    paid_at = COALESCE(orders.paid_at, EXCLUDED.paid_at),
                    shipped_at = COALESCE(orders.shipped_at, EXCLUDED.shipped_at),
                    delivered_at = COALESCE(orders.delivered_at, EXCLUDED.delivered_at),
                    cancelled_at = COALESCE(orders.cancelled_at, EXCLUDED.cancelled_at),
                    cancel_reason = COALESCE(EXCLUDED.cancel_reason, orders.cancel_reason),
                    order_version = EXCLUDED.order_version, updated_at = EXCLUDED.updated_at
                WHERE orders.order_version < EXCLUDED.order_version
                """, new MapSqlParameterSource()
                .addValue("id", e.orderId())
                .addValue("number", e.orderNumber())
                .addValue("userId", e.userId())
                .addValue("status", e.status())
                .addValue("total", e.totalAmount())
                .addValue("currency", e.currency() == null ? "INR" : e.currency())
                .addValue("itemCount", items.values().stream().mapToInt(OrderEvent.Item::quantity).sum())
                .addValue("placedAt", when(type, EventTypes.ORDER_PLACED, at))
                .addValue("paidAt", when(type, EventTypes.ORDER_CONFIRMED, at))
                .addValue("shippedAt", when(type, EventTypes.ORDER_SHIPPED, at))
                .addValue("deliveredAt", when(type, EventTypes.ORDER_DELIVERED, at))
                .addValue("cancelledAt", when(type, EventTypes.ORDER_CANCELLED, at))
                .addValue("reason", EventTypes.ORDER_CANCELLED.equals(type) ? e.reason() : null)
                .addValue("version", e.orderVersion())
                .addValue("now", Timestamp.from(clock.instant())));
        if (rows == 0) {
            log.debug("Skipped outdated {} for {} (v{})", type, e.orderNumber(), e.orderVersion());
            return false;
        }
        if (!items.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO order_items (order_id, product_id, sku, product_name, quantity, unit_price)
                    VALUES (:orderId, :productId, :sku, :name, :quantity, :unitPrice) ON CONFLICT DO NOTHING
                    """, items.values().stream().map(i -> new MapSqlParameterSource("orderId", e.orderId())
                    .addValue("productId", i.productId()).addValue("sku", i.sku()).addValue("name", i.productName())
                    .addValue("quantity", i.quantity())
                    .addValue("unitPrice", i.unitPrice() == null ? java.math.BigDecimal.ZERO : i.unitPrice()))
                    .toArray(SqlParameterSource[]::new));
        }
        String total = Money.format(e.totalAmount(), e.currency());
        switch (type) {
            case EventTypes.ORDER_CONFIRMED -> activity.add(e.eventId(), at, "ORDER_PAID", Severity.INFO,
                    "Order " + e.orderNumber() + " paid: " + total, e.orderNumber(), null);
            case EventTypes.ORDER_SHIPPED -> activity.add(e.eventId(), at, "ORDER_SHIPPED", Severity.INFO,
                    "Order " + e.orderNumber() + " shipped", e.orderNumber(), null);
            case EventTypes.ORDER_DELIVERED -> activity.add(e.eventId(), at, "ORDER_DELIVERED", Severity.INFO,
                    "Order " + e.orderNumber() + " delivered", e.orderNumber(), null);
            case EventTypes.ORDER_CANCELLED -> {
                boolean wasPaid = Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT paid_at IS NOT NULL FROM orders WHERE order_id = :id",
                        new MapSqlParameterSource("id", e.orderId()), Boolean.class));
                String why = e.reason() == null ? "" : ": " + e.reason();
                if (wasPaid) {
                    activity.add(e.eventId(), at, "PAID_ORDER_CANCELLED", Severity.WARNING,
                            "Paid order " + e.orderNumber() + " cancelled" + why + " (" + total + " to refund)", e.orderNumber(), null);
                } else {
                    activity.add(e.eventId(), at, "ORDER_CANCELLED", Severity.INFO,
                            "Order " + e.orderNumber() + " cancelled before payment" + why, e.orderNumber(), null);
                }
            }
            default -> {
            }
        }
        return true;
    }

    // ---------- payment-events ----------

    @Transactional
    public boolean apply(PaymentEvent e) {
        if (e.eventType() == null || !EventTypes.PAYMENT.contains(e.eventType())) {
            return false;
        }
        if (e.eventId() == null || e.orderId() == null) {
            throw new IllegalArgumentException("Payment event " + e.eventId() + " has no eventId or orderId");
        }
        Instant at = at(e.occurredAt());
        int inserted = jdbc.update("""
                INSERT INTO payment_events (event_id, event_type, order_id, order_number, amount, method, reason, occurred_at)
                VALUES (:id, :type, :orderId, :number, :amount, :method, :reason, :at) ON CONFLICT (event_id) DO NOTHING
                """, new MapSqlParameterSource("id", e.eventId()).addValue("type", e.eventType())
                .addValue("orderId", e.orderId()).addValue("number", e.orderNumber()).addValue("amount", e.amount())
                .addValue("method", e.method()).addValue("reason", e.reason()).addValue("at", Timestamp.from(at)));
        if (inserted == 0) {
            return false;
        }
        String amount = Money.format(e.amount(), e.currency());
        switch (e.eventType()) {
            case EventTypes.PAYMENT_FAILED -> activity.add(e.eventId(), at, "PAYMENT_FAILED", Severity.WARNING,
                    "Payment of " + amount + " for " + e.orderNumber() + " failed"
                            + (e.reason() == null ? "" : ": " + e.reason()), e.orderNumber(), null);
            case EventTypes.PAYMENT_REFUNDED -> {
                jdbc.update("UPDATE orders SET refunded_amount = :amount, refunded_at = :at WHERE order_id = :orderId",
                        new MapSqlParameterSource("amount", e.amount()).addValue("at", Timestamp.from(at))
                                .addValue("orderId", e.orderId()));
                activity.add(e.eventId(), at, "REFUND_COMPLETED", Severity.INFO,
                        "Refund of " + amount + " for " + e.orderNumber() + " completed", e.orderNumber(), null);
            }
            default -> {
            }
        }
        return true;
    }

    // ---------- inventory-events ----------

    @Transactional
    public boolean apply(InventoryEvent e) {
        if (e.eventType() == null || !EventTypes.STOCK.contains(e.eventType())) {
            return false;
        }
        if (e.productId() == null) {
            throw new IllegalArgumentException("Inventory event " + e.eventId() + " has no productId");
        }
        Instant at = at(e.occurredAt());
        int rows = jdbc.update("""
                INSERT INTO stock_levels (product_id, sku, product_name, quantity_on_hand, quantity_reserved,
                                          available_quantity, low_stock_threshold, low_stock, inventory_version, updated_at)
                VALUES (:id, :sku, :name, :onHand, :reserved, :available, :threshold, :low, :version, :at)
                ON CONFLICT (product_id) DO UPDATE SET sku = EXCLUDED.sku, product_name = EXCLUDED.product_name,
                    quantity_on_hand = EXCLUDED.quantity_on_hand, quantity_reserved = EXCLUDED.quantity_reserved,
                    available_quantity = EXCLUDED.available_quantity, low_stock_threshold = EXCLUDED.low_stock_threshold,
                    low_stock = EXCLUDED.low_stock, inventory_version = EXCLUDED.inventory_version,
                    updated_at = EXCLUDED.updated_at
                WHERE stock_levels.inventory_version < EXCLUDED.inventory_version
                """, new MapSqlParameterSource("id", e.productId()).addValue("sku", e.sku()).addValue("name", e.productName())
                .addValue("onHand", e.quantityOnHand()).addValue("reserved", e.quantityReserved())
                .addValue("available", e.availableQuantity()).addValue("threshold", e.lowStockThreshold())
                .addValue("low", e.lowStock()).addValue("version", e.inventoryVersion()).addValue("at", Timestamp.from(at)));
        if (!EventTypes.INVENTORY_LOW.equals(e.eventType())) {
            return rows > 0;
        }
        // inventory-service sends INVENTORY_UPDATED and then INVENTORY_LOW for the same change (same version): the alert
        // belongs in the feed unless the stock has changed again since. A repeated alert adds nothing (same event id).
        Long stored = jdbc.queryForObject("SELECT inventory_version FROM stock_levels WHERE product_id = :id",
                new MapSqlParameterSource("id", e.productId()), Long.class);
        if (stored != null && stored > e.inventoryVersion()) {
            return false;
        }
        String name = e.productName() != null ? e.productName() : e.sku();
        return activity.add(e.eventId(), at, "LOW_STOCK", Severity.WARNING, "Low stock: " + name + " (" + e.sku() + ") - "
                + e.availableQuantity() + " left, threshold " + e.lowStockThreshold(), null, e.productId()) || rows > 0;
    }

    // ---------- shipment-events ----------

    @Transactional
    public boolean apply(ShipmentEvent e) {
        if (!EventTypes.SHIPMENT_DELIVERY_FAILED.equals(e.eventType())) {
            return false;
        }
        if (e.eventId() == null || e.orderId() == null) {
            throw new IllegalArgumentException("Shipment event " + e.eventId() + " has no eventId or orderId");
        }
        Instant at = at(e.occurredAt());
        int inserted = jdbc.update("""
                INSERT INTO delivery_failures (event_id, order_id, order_number, carrier, reason, occurred_at)
                VALUES (:id, :orderId, :number, :carrier, :reason, :at) ON CONFLICT (event_id) DO NOTHING
                """, new MapSqlParameterSource("id", e.eventId()).addValue("orderId", e.orderId())
                .addValue("number", e.orderNumber()).addValue("carrier", e.carrier()).addValue("reason", e.note())
                .addValue("at", Timestamp.from(at)));
        if (inserted > 0) {
            activity.add(e.eventId(), at, "DELIVERY_FAILED", Severity.WARNING, "Delivery of " + e.orderNumber() + " failed"
                    + (e.carrier() == null ? "" : " (" + e.carrier() + ")") + (e.note() == null ? "" : ": " + e.note()),
                    e.orderNumber(), null);
        }
        return inserted > 0;
    }

    // ---------- Helpers ----------

    private Instant at(Instant occurredAt) {
        return occurredAt != null ? occurredAt : clock.instant();
    }

    private static Timestamp when(String type, String expected, Instant at) {
        return type.equals(expected) ? Timestamp.from(at) : null;
    }
}

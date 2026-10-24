package com.ecommerce.recommendationservice.orders;

import com.ecommerce.recommendationservice.event.EventTypes;
import com.ecommerce.recommendationservice.event.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Learns from order-events what customers buy:
 * - ORDER_CONFIRMED (paid): the order is recorded once (a repeated event changes nothing), each product's sales for the
 *   day go up, and every pair of products in the order counts one more "bought together";
 * - ORDER_CANCELLED of a paid order: the same counts go back down (once). Cancelling an unpaid order changes nothing.
 * Both happen in one transaction with the order row, so counts and orders always agree.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseHistory {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Transactional
    public boolean apply(OrderEvent e) {
        if (e.eventType() == null) {
            throw new IllegalArgumentException("Order event " + e.eventId() + " has no type");
        }
        return switch (e.eventType()) {
            case EventTypes.ORDER_CONFIRMED -> confirmed(e);
            case EventTypes.ORDER_CANCELLED -> cancelled(e);
            default -> false;
        };
    }

    private boolean confirmed(OrderEvent e) {
        if (e.orderId() == null || e.userId() == null || e.orderNumber() == null || e.items() == null || e.items().isEmpty()) {
            throw new IllegalArgumentException("Order event " + e.eventId() + " is missing required fields");
        }
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (OrderEvent.Item item : e.items()) {
            if (item == null || item.productId() == null || item.quantity() <= 0) {
                throw new IllegalArgumentException("Order " + e.orderNumber() + " has an invalid item");
            }
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }
        Instant confirmedAt = e.occurredAt() != null ? e.occurredAt() : clock.instant();
        int inserted = jdbc.update("""
                INSERT INTO orders (order_id, order_number, user_id, confirmed_at) VALUES (:id, :number, :userId, :at)
                ON CONFLICT (order_id) DO NOTHING
                """, new MapSqlParameterSource("id", e.orderId()).addValue("number", e.orderNumber())
                .addValue("userId", e.userId()).addValue("at", Timestamp.from(confirmedAt)));
        if (inserted == 0) {
            log.debug("Order {} already counted", e.orderNumber());
            return false;
        }
        jdbc.batchUpdate("INSERT INTO order_items (order_id, product_id, quantity) VALUES (:orderId, :productId, :quantity)",
                quantities.entrySet().stream().map(q -> new MapSqlParameterSource("orderId", e.orderId())
                        .addValue("productId", q.getKey()).addValue("quantity", q.getValue())).toArray(SqlParameterSource[]::new));
        count(quantities, day(confirmedAt), +1);
        log.info("Order {} counted: {} product(s)", e.orderNumber(), quantities.size());
        return true;
    }

    private boolean cancelled(OrderEvent e) {
        if (e.orderId() == null) {
            throw new IllegalArgumentException("Order event " + e.eventId() + " has no orderId");
        }
        List<Timestamp> confirmedAt = jdbc.queryForList("""
                UPDATE orders SET cancelled_at = :now WHERE order_id = :id AND cancelled_at IS NULL RETURNING confirmed_at
                """, new MapSqlParameterSource("id", e.orderId()).addValue("now", Timestamp.from(clock.instant())), Timestamp.class);
        if (confirmedAt.isEmpty()) {
            return false; // never paid, or already taken back
        }
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        jdbc.query("SELECT product_id, quantity FROM order_items WHERE order_id = :id",
                new MapSqlParameterSource("id", e.orderId()),
                rs -> {
                    quantities.put(rs.getObject("product_id", UUID.class), rs.getInt("quantity"));
                });
        count(quantities, day(confirmedAt.get(0).toInstant()), -1);
        log.info("Paid order {} cancelled: its purchases no longer count", e.orderNumber());
        return true;
    }

    /** sign +1 adds the order to the counts, -1 takes it back. */
    private void count(Map<UUID, Integer> quantities, LocalDate day, int sign) {
        List<SqlParameterSource> sales = new ArrayList<>();
        quantities.forEach((productId, quantity) -> sales.add(new MapSqlParameterSource("productId", productId)
                .addValue("day", Date.valueOf(day)).addValue("units", sign * quantity).addValue("orders", sign)));
        jdbc.batchUpdate("""
                INSERT INTO daily_sales (product_id, sale_date, units, orders) VALUES (:productId, :day, :units, :orders)
                ON CONFLICT (product_id, sale_date) DO UPDATE SET units = daily_sales.units + EXCLUDED.units,
                    orders = daily_sales.orders + EXCLUDED.orders
                """, sales.toArray(SqlParameterSource[]::new));

        List<SqlParameterSource> pairs = new ArrayList<>();
        for (UUID a : quantities.keySet()) {
            for (UUID b : quantities.keySet()) {
                if (!a.equals(b)) {
                    pairs.add(new MapSqlParameterSource("a", a).addValue("b", b).addValue("delta", sign));
                }
            }
        }
        if (!pairs.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO bought_together (product_id, other_product_id, orders) VALUES (:a, :b, :delta)
                    ON CONFLICT (product_id, other_product_id) DO UPDATE SET orders = bought_together.orders + EXCLUDED.orders
                    """, pairs.toArray(SqlParameterSource[]::new));
        }
    }

    private static LocalDate day(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC);
    }
}

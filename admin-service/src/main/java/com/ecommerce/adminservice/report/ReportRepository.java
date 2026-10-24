package com.ecommerce.adminservice.report;

import com.ecommerce.adminservice.dto.response.ActivityItem;
import com.ecommerce.adminservice.dto.response.DailySales;
import com.ecommerce.adminservice.dto.response.LowStockItem;
import com.ecommerce.adminservice.dto.response.PipelineStage;
import com.ecommerce.adminservice.dto.response.TopProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The SQL behind the dashboard. Dates are grouped in the shop's time zone. */
@Repository
@RequiredArgsConstructor
public class ReportRepository {

    private final NamedParameterJdbcTemplate jdbc;

    record SalesTotals(BigDecimal gross, long paidOrders, BigDecimal refunds, long refundCount, long unitsSold) {
    }

    SalesTotals sales(ReportPeriod p) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT (SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE paid_at >= :start AND paid_at < :end) AS gross,
                       (SELECT COUNT(*) FROM orders WHERE paid_at >= :start AND paid_at < :end) AS paid,
                       (SELECT COALESCE(SUM(refunded_amount), 0) FROM orders WHERE refunded_at >= :start AND refunded_at < :end) AS refunds,
                       (SELECT COUNT(*) FROM orders WHERE refunded_at >= :start AND refunded_at < :end) AS refund_count,
                       (SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi JOIN orders o ON o.order_id = oi.order_id
                        WHERE o.paid_at >= :start AND o.paid_at < :end AND o.cancelled_at IS NULL) AS units
                """, range(p));
        return new SalesTotals((BigDecimal) row.get("gross"), n(row, "paid"), (BigDecimal) row.get("refunds"),
                n(row, "refund_count"), n(row, "units"));
    }

    Map<String, Object> orderCounts(ReportPeriod p) {
        return jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE placed_at >= :start AND placed_at < :end) AS placed,
                       COUNT(*) FILTER (WHERE paid_at >= :start AND paid_at < :end) AS paid,
                       COUNT(*) FILTER (WHERE cancelled_at >= :start AND cancelled_at < :end AND paid_at IS NULL) AS cancelled_unpaid,
                       COUNT(*) FILTER (WHERE cancelled_at >= :start AND cancelled_at < :end AND paid_at IS NOT NULL) AS cancelled_paid,
                       COUNT(*) FILTER (WHERE shipped_at >= :start AND shipped_at < :end) AS shipped,
                       COUNT(*) FILTER (WHERE delivered_at >= :start AND delivered_at < :end) AS delivered
                FROM orders
                """, range(p));
    }

    Map<String, Object> paymentCounts(ReportPeriod p) {
        return jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE event_type = 'PAYMENT_SUCCEEDED') AS succeeded,
                       COUNT(*) FILTER (WHERE event_type = 'PAYMENT_FAILED') AS failed
                FROM payment_events WHERE occurred_at >= :start AND occurred_at < :end
                """, range(p));
    }

    /** Customers with a paid order in the period, and those whose first paid order ever is in it. */
    Map<String, Object> buyers(ReportPeriod p) {
        return jdbc.queryForMap("""
                SELECT COUNT(DISTINCT user_id) AS buyers,
                       COUNT(DISTINCT user_id) FILTER (WHERE NOT EXISTS (
                           SELECT 1 FROM orders earlier WHERE earlier.user_id = o.user_id AND earlier.paid_at < :start)) AS new_buyers
                FROM orders o WHERE o.paid_at >= :start AND o.paid_at < :end
                """, range(p));
    }

    Map<String, Object> workload(Instant since7Days) {
        return jdbc.queryForMap("""
                SELECT (SELECT COUNT(*) FROM orders WHERE status = 'CONFIRMED') AS to_ship,
                       (SELECT MIN(paid_at) FROM orders WHERE status = 'CONFIRMED') AS oldest_to_ship,
                       (SELECT COUNT(*) FROM orders WHERE status = 'SHIPPED') AS in_transit,
                       (SELECT COUNT(*) FROM stock_levels WHERE low_stock) AS low_stock,
                       (SELECT COUNT(*) FROM delivery_failures WHERE occurred_at >= :since) AS failures
                """, new MapSqlParameterSource("since", Timestamp.from(since7Days)));
    }

    List<DailySales> daily(ReportPeriod p, ZoneId zone) {
        MapSqlParameterSource params = range(p).addValue("from", Date.valueOf(p.from())).addValue("to", Date.valueOf(p.to()))
                .addValue("zone", zone.getId());
        return jdbc.query("""
                WITH days AS (SELECT CAST(d AS date) AS day FROM generate_series(CAST(:from AS date), CAST(:to AS date), INTERVAL '1 day') d),
                     paid AS (SELECT CAST(paid_at AT TIME ZONE :zone AS date) AS day, COUNT(*) AS orders, SUM(total_amount) AS gross
                              FROM orders WHERE paid_at >= :start AND paid_at < :end GROUP BY 1),
                     refunded AS (SELECT CAST(refunded_at AT TIME ZONE :zone AS date) AS day, SUM(refunded_amount) AS refunds
                                  FROM orders WHERE refunded_at >= :start AND refunded_at < :end GROUP BY 1),
                     units AS (SELECT CAST(o.paid_at AT TIME ZONE :zone AS date) AS day, SUM(oi.quantity) AS units
                               FROM orders o JOIN order_items oi ON oi.order_id = o.order_id
                               WHERE o.paid_at >= :start AND o.paid_at < :end AND o.cancelled_at IS NULL GROUP BY 1)
                SELECT d.day, COALESCE(p.orders, 0) AS orders, COALESCE(p.gross, 0) AS gross,
                       COALESCE(r.refunds, 0) AS refunds, COALESCE(u.units, 0) AS units
                FROM days d LEFT JOIN paid p ON p.day = d.day LEFT JOIN refunded r ON r.day = d.day
                            LEFT JOIN units u ON u.day = d.day
                ORDER BY d.day
                """, params, (rs, i) -> {
            BigDecimal gross = rs.getBigDecimal("gross");
            BigDecimal refunds = rs.getBigDecimal("refunds");
            return new DailySales(rs.getDate("day").toLocalDate(), rs.getLong("orders"), gross, refunds,
                    gross.subtract(refunds), rs.getLong("units"));
        });
    }

    List<TopProduct> topProducts(ReportPeriod p, boolean byUnits, int limit) {
        String order = byUnits ? "units DESC, revenue DESC" : "revenue DESC, units DESC";
        return jdbc.query("""
                SELECT oi.product_id, MAX(oi.sku) AS sku, MAX(oi.product_name) AS name, SUM(oi.quantity) AS units,
                       SUM(oi.quantity * oi.unit_price) AS revenue, COUNT(DISTINCT oi.order_id) AS orders
                FROM order_items oi JOIN orders o ON o.order_id = oi.order_id
                WHERE o.paid_at >= :start AND o.paid_at < :end AND o.cancelled_at IS NULL
                GROUP BY oi.product_id ORDER BY\s""" + order + ", oi.product_id LIMIT :limit",
                range(p).addValue("limit", limit),
                (rs, i) -> new TopProduct(rs.getObject("product_id", UUID.class), rs.getString("sku"), rs.getString("name"),
                        rs.getLong("units"), rs.getBigDecimal("revenue"), rs.getLong("orders")));
    }

    /** Orders per current status; for statuses that wait for something, when the oldest one got there. */
    List<PipelineStage> pipeline() {
        return jdbc.query("""
                SELECT status, COUNT(*) AS orders,
                       MIN(CASE status WHEN 'PENDING_PAYMENT' THEN placed_at WHEN 'CONFIRMED' THEN paid_at
                                       WHEN 'SHIPPED' THEN shipped_at END) AS oldest
                FROM orders GROUP BY status
                ORDER BY CASE status WHEN 'PENDING_PAYMENT' THEN 1 WHEN 'CONFIRMED' THEN 2 WHEN 'SHIPPED' THEN 3
                                     WHEN 'DELIVERED' THEN 4 WHEN 'CANCELLED' THEN 5 ELSE 6 END
                """, new MapSqlParameterSource(), (rs, i) -> {
            Timestamp oldest = rs.getTimestamp("oldest");
            return new PipelineStage(rs.getString("status"), rs.getLong("orders"), oldest == null ? null : oldest.toInstant());
        });
    }

    List<LowStockItem> lowStock(int limit) {
        return jdbc.query("""
                SELECT * FROM stock_levels WHERE low_stock ORDER BY available_quantity, product_name, product_id LIMIT :limit
                """, new MapSqlParameterSource("limit", limit), (rs, i) -> new LowStockItem(
                rs.getObject("product_id", UUID.class), rs.getString("sku"), rs.getString("product_name"),
                rs.getInt("available_quantity"), rs.getInt("quantity_on_hand"), rs.getInt("quantity_reserved"),
                rs.getInt("low_stock_threshold"), rs.getInt("available_quantity") == 0, rs.getTimestamp("updated_at").toInstant()));
    }

    long activityCount(String severity) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM activity WHERE (CAST(:severity AS varchar) IS NULL OR severity = :severity)",
                new MapSqlParameterSource("severity", severity), Long.class);
        return n == null ? 0 : n;
    }

    List<ActivityItem> activity(String severity, int page, int size) {
        return jdbc.query("""
                SELECT * FROM activity WHERE (CAST(:severity AS varchar) IS NULL OR severity = :severity)
                ORDER BY occurred_at DESC, event_id LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource("severity", severity).addValue("limit", size).addValue("offset", (long) page * size),
                (rs, i) -> new ActivityItem(rs.getTimestamp("occurred_at").toInstant(), rs.getString("kind"),
                        rs.getString("severity"), rs.getString("message"), rs.getString("order_number"),
                        rs.getObject("product_id", UUID.class)));
    }

    private static MapSqlParameterSource range(ReportPeriod p) {
        return new MapSqlParameterSource("start", Timestamp.from(p.start())).addValue("end", Timestamp.from(p.end()));
    }

    static long n(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}

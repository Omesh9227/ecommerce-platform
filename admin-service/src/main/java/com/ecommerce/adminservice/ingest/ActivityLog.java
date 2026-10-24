package com.ecommerce.adminservice.ingest;

import com.ecommerce.adminservice.config.DashboardProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * The admin's activity feed: one line per noteworthy event (keyed by the event id, so a repeated event adds nothing),
 * written in the same transaction as the report data. Lines older than app.dashboard.activity-days are removed.
 */
@Component
@RequiredArgsConstructor
public class ActivityLog {

    public enum Severity { INFO, WARNING }

    private final NamedParameterJdbcTemplate jdbc;
    private final DashboardProperties properties;
    private final Clock clock;

    /** @return false if this event already has its line */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean add(UUID eventId, Instant at, String kind, Severity severity, String message, String orderNumber, UUID productId) {
        if (eventId == null) {
            return false;
        }
        int inserted = jdbc.update("""
                INSERT INTO activity (event_id, occurred_at, kind, severity, message, order_number, product_id)
                VALUES (:id, :at, :kind, :severity, :message, :orderNumber, :productId) ON CONFLICT (event_id) DO NOTHING
                """, new MapSqlParameterSource("id", eventId).addValue("at", Timestamp.from(at)).addValue("kind", kind)
                .addValue("severity", severity.name()).addValue("message", message.length() > 500 ? message.substring(0, 500) : message)
                .addValue("orderNumber", orderNumber).addValue("productId", productId));
        jdbc.update("DELETE FROM activity WHERE occurred_at < :cutoff", new MapSqlParameterSource("cutoff",
                Timestamp.from(clock.instant().minus(Duration.ofDays(properties.activityDays())))));
        return inserted > 0;
    }
}

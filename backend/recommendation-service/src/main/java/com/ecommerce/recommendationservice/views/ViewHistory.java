package com.ecommerce.recommendationservice.views;

import com.ecommerce.recommendationservice.config.RecommendationProperties;
import com.ecommerce.recommendationservice.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What each customer looked at. The first time a customer views a product, it counts one "viewed together" with each
 * of their recent views (both ways) - reloading a page never inflates the counts. A customer's views are recorded one
 * at a time (advisory lock per customer), so two pages opened at once still count as viewed together.
 * Each customer keeps their {@value #KEPT_PER_CUSTOMER} most recent products.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewHistory {

    static final int KEPT_PER_CUSTOMER = 200;

    private final NamedParameterJdbcTemplate jdbc;
    private final RecommendationProperties properties;
    private final Clock clock;

    @Transactional
    public void record(UUID userId, UUID productId) {
        jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT pg_advisory_xact_lock(:key)) AS locked",
                new MapSqlParameterSource("key", userId.getMostSignificantBits() ^ userId.getLeastSignificantBits()), Long.class);
        Boolean known = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM products WHERE product_id = :id AND name IS NOT NULL AND status = 'ACTIVE')",
                new MapSqlParameterSource("id", productId), Boolean.class);
        if (!Boolean.TRUE.equals(known)) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }
        MapSqlParameterSource p = new MapSqlParameterSource("userId", userId).addValue("productId", productId)
                .addValue("now", Timestamp.from(clock.instant())).addValue("history", properties.viewHistory());
        Boolean firstView = jdbc.queryForObject("""
                INSERT INTO product_views (user_id, product_id, view_count, first_viewed_at, last_viewed_at)
                VALUES (:userId, :productId, 1, :now, :now)
                ON CONFLICT (user_id, product_id) DO UPDATE SET view_count = product_views.view_count + 1,
                    last_viewed_at = EXCLUDED.last_viewed_at
                RETURNING (xmax = 0)
                """, p, Boolean.class);
        if (Boolean.TRUE.equals(firstView)) {
            List<UUID> others = jdbc.queryForList("""
                    SELECT product_id FROM product_views WHERE user_id = :userId AND product_id <> :productId
                    ORDER BY last_viewed_at DESC LIMIT :history
                    """, p, UUID.class);
            List<SqlParameterSource> pairs = new ArrayList<>();
            for (UUID other : others) {
                pairs.add(new MapSqlParameterSource("a", productId).addValue("b", other));
                pairs.add(new MapSqlParameterSource("a", other).addValue("b", productId));
            }
            if (!pairs.isEmpty()) {
                jdbc.batchUpdate("""
                        INSERT INTO viewed_together (product_id, other_product_id, customers) VALUES (:a, :b, 1)
                        ON CONFLICT (product_id, other_product_id) DO UPDATE SET customers = viewed_together.customers + 1
                        """, pairs.toArray(SqlParameterSource[]::new));
            }
            jdbc.update("""
                    DELETE FROM product_views WHERE user_id = :userId AND product_id NOT IN (
                        SELECT product_id FROM product_views WHERE user_id = :userId ORDER BY last_viewed_at DESC LIMIT :kept)
                    """, new MapSqlParameterSource("userId", userId).addValue("kept", KEPT_PER_CUSTOMER));
        }
    }

    /** Forgets what this customer viewed ("viewed together" counts are anonymous and stay). */
    @Transactional
    public int clear(UUID userId) {
        return jdbc.update("DELETE FROM product_views WHERE user_id = :userId", new MapSqlParameterSource("userId", userId));
    }
}

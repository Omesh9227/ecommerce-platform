package com.ecommerce.recommendationservice.catalog;

import com.ecommerce.recommendationservice.event.CategoryEvent;
import com.ecommerce.recommendationservice.event.EventTypes;
import com.ecommerce.recommendationservice.event.InventoryEvent;
import com.ecommerce.recommendationservice.event.ProductEvent;
import com.ecommerce.recommendationservice.event.ReviewEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;

/**
 * Keeps the copy of the catalog that recommendations are made from: what each product is, whether it can be sold
 * (ACTIVE and in stock) and how well it is rated. Each source has its own version column and only newer data is
 * applied, so events may arrive late, twice or in any order. Product events of the same version are applied again
 * (a republished catalog fills in fields older events didn't carry).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogIndexer {

    private static final BigDecimal FIVE = BigDecimal.valueOf(5);

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Transactional
    public boolean apply(ProductEvent e) {
        if (e.eventType() == null || !EventTypes.PRODUCT.contains(e.eventType())) {
            return false;
        }
        if (e.productId() == null || blank(e.name()) || blank(e.status())) {
            throw new IllegalArgumentException("Product event " + e.eventId() + " is missing required fields");
        }
        String imageUrl = e.imageUrls() == null ? null : e.imageUrls().stream().filter(u -> !blank(u)).findFirst().orElse(null);
        int rows = jdbc.update("""
                INSERT INTO products (product_id, name, brand, category_id, status, selling_price, image_url,
                                      product_version, updated_at)
                VALUES (:id, :name, :brand, :categoryId, :status, :sellingPrice, :imageUrl, :version, :now)
                ON CONFLICT (product_id) DO UPDATE SET name = EXCLUDED.name, brand = EXCLUDED.brand,
                    category_id = EXCLUDED.category_id, status = EXCLUDED.status,
                    selling_price = EXCLUDED.selling_price, image_url = EXCLUDED.image_url,
                    product_version = EXCLUDED.product_version, updated_at = EXCLUDED.updated_at
                WHERE products.product_version <= EXCLUDED.product_version
                """, new MapSqlParameterSource()
                .addValue("id", e.productId())
                .addValue("name", e.name())
                .addValue("brand", blank(e.brand()) ? null : e.brand().trim())
                .addValue("categoryId", e.categoryId())
                .addValue("status", e.status())
                .addValue("sellingPrice", e.sellingPrice())
                .addValue("imageUrl", imageUrl)
                .addValue("version", e.productVersion())
                .addValue("now", now()));
        if (rows == 0) {
            log.debug("Skipped outdated {} for product {} (v{})", e.eventType(), e.productId(), e.productVersion());
        }
        return rows > 0;
    }

    @Transactional
    public boolean apply(CategoryEvent e) {
        if (e.eventType() == null || e.categoryId() == null) {
            throw new IllegalArgumentException("Category event " + e.eventId() + " has no type or categoryId");
        }
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", e.categoryId())
                .addValue("name", e.name())
                .addValue("parentId", e.parentId())
                .addValue("version", e.categoryVersion())
                .addValue("now", now());
        return switch (e.eventType()) {
            case EventTypes.CATEGORY_CREATED, EventTypes.CATEGORY_UPDATED -> {
                if (blank(e.name())) {
                    throw new IllegalArgumentException("Category event " + e.eventId() + " has no name");
                }
                yield jdbc.update("""
                        INSERT INTO categories (id, name, parent_id, category_version, updated_at)
                        VALUES (:id, :name, :parentId, :version, :now)
                        ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, parent_id = EXCLUDED.parent_id,
                            category_version = EXCLUDED.category_version, updated_at = EXCLUDED.updated_at
                        WHERE categories.category_version < EXCLUDED.category_version
                        """, p) > 0;
            }
            case EventTypes.CATEGORY_DELETED ->
                    jdbc.update("DELETE FROM categories WHERE id = :id AND category_version < :version", p) > 0;
            default -> false;
        };
    }

    @Transactional
    public boolean apply(InventoryEvent e) {
        if (e.eventType() == null || !EventTypes.STOCK.contains(e.eventType())) {
            return false;
        }
        if (e.productId() == null || e.availableQuantity() < 0) {
            throw new IllegalArgumentException("Inventory event " + e.eventId() + " has no productId or a negative stock");
        }
        return jdbc.update("""
                INSERT INTO products (product_id, available_quantity, inventory_version, updated_at)
                VALUES (:id, :available, :version, :now)
                ON CONFLICT (product_id) DO UPDATE SET available_quantity = EXCLUDED.available_quantity,
                    inventory_version = EXCLUDED.inventory_version, updated_at = EXCLUDED.updated_at
                WHERE products.inventory_version < EXCLUDED.inventory_version
                """, new MapSqlParameterSource()
                .addValue("id", e.productId())
                .addValue("available", e.availableQuantity())
                .addValue("version", e.inventoryVersion())
                .addValue("now", now())) > 0;
    }

    @Transactional
    public boolean apply(ReviewEvent e) {
        if (!EventTypes.PRODUCT_RATING_UPDATED.equals(e.eventType())) {
            return false;
        }
        if (e.productId() == null || e.averageRating() == null || e.averageRating().signum() < 0
                || e.averageRating().compareTo(FIVE) > 0 || e.reviewCount() < 0) {
            throw new IllegalArgumentException("Review event " + e.eventId() + " has no productId or an invalid rating");
        }
        return jdbc.update("""
                INSERT INTO products (product_id, average_rating, review_count, rating_version, updated_at)
                VALUES (:id, :rating, :count, :version, :now)
                ON CONFLICT (product_id) DO UPDATE SET average_rating = EXCLUDED.average_rating,
                    review_count = EXCLUDED.review_count, rating_version = EXCLUDED.rating_version,
                    updated_at = EXCLUDED.updated_at
                WHERE products.rating_version < EXCLUDED.rating_version
                """, new MapSqlParameterSource()
                .addValue("id", e.productId())
                .addValue("rating", e.averageRating())
                .addValue("count", e.reviewCount())
                .addValue("version", e.ratingVersion())
                .addValue("now", now())) > 0;
    }

    private Timestamp now() {
        return Timestamp.from(clock.instant());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

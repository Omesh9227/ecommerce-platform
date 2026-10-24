package com.ecommerce.searchservice.index;

import com.ecommerce.searchservice.event.CategoryEvent;
import com.ecommerce.searchservice.event.EventTypes;
import com.ecommerce.searchservice.event.InventoryEvent;
import com.ecommerce.searchservice.event.ProductEvent;
import com.ecommerce.searchservice.event.ReviewEvent;
import com.ecommerce.searchservice.search.QueryText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the search copy of the catalog up to date from events. Every source has its own version column, and an
 * update is applied only if its version is not older than what is stored, so events may arrive late, twice or out
 * of order. Product events of the same version are applied again (a republished catalog fills in fields that older
 * events didn't carry). Words of names, brands and categories go into the spelling dictionary.
 * Product and category updates for the same category are serialized (see {@link #lockCategory}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogIndexer {

    private static final BigDecimal FIVE = BigDecimal.valueOf(5);

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    /** @return true if the product was (re)indexed, false if the event was old or of another type */
    @Transactional
    public boolean apply(ProductEvent e) {
        if (e.eventType() == null || !EventTypes.PRODUCT.contains(e.eventType())) {
            return false;
        }
        if (e.productId() == null || blank(e.name()) || blank(e.sku()) || blank(e.status())) {
            throw new IllegalArgumentException("Product event " + e.eventId() + " is missing required fields");
        }
        lockCategory(e.categoryId());
        String imageUrl = e.imageUrls() == null ? null : e.imageUrls().stream().filter(u -> !blank(u)).findFirst().orElse(null);
        Instant listedAt = e.createdAt() != null ? e.createdAt() : e.occurredAt();
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", e.productId())
                .addValue("sku", e.sku())
                .addValue("name", e.name())
                .addValue("brand", blank(e.brand()) ? null : e.brand().trim())
                .addValue("description", e.description())
                .addValue("categoryId", e.categoryId())
                .addValue("status", e.status())
                .addValue("price", e.price())
                .addValue("sellingPrice", e.sellingPrice())
                .addValue("discount", e.discountPercent())
                .addValue("imageUrl", imageUrl)
                .addValue("listedAt", timestamp(listedAt))
                .addValue("version", e.productVersion())
                .addValue("now", timestamp(clock.instant()));
        int rows = jdbc.update("""
                INSERT INTO search_products (product_id, sku, name, brand, description, category_id, category_name, status,
                                             price, selling_price, discount_percent, image_url, listed_at,
                                             product_version, updated_at)
                VALUES (:id, :sku, :name, :brand, :description, :categoryId,
                        (SELECT c.name FROM categories c WHERE c.id = :categoryId), :status,
                        :price, :sellingPrice, :discount, :imageUrl, :listedAt, :version, :now)
                ON CONFLICT (product_id) DO UPDATE SET
                    sku = EXCLUDED.sku, name = EXCLUDED.name, brand = EXCLUDED.brand, description = EXCLUDED.description,
                    category_id = EXCLUDED.category_id, category_name = EXCLUDED.category_name, status = EXCLUDED.status,
                    price = EXCLUDED.price, selling_price = EXCLUDED.selling_price,
                    discount_percent = EXCLUDED.discount_percent, image_url = EXCLUDED.image_url,
                    listed_at = COALESCE(search_products.listed_at, EXCLUDED.listed_at),
                    product_version = EXCLUDED.product_version, updated_at = EXCLUDED.updated_at
                WHERE search_products.product_version <= EXCLUDED.product_version
                """, p);
        if (rows == 0) {
            log.debug("Skipped outdated {} for product {} (v{})", e.eventType(), e.productId(), e.productVersion());
            return false;
        }
        addWords(QueryText.dictionaryWords(e.name(), e.brand()));
        log.debug("Indexed {} {} ({}) v{}", e.eventType(), e.productId(), e.status(), e.productVersion());
        return true;
    }

    @Transactional
    public boolean apply(CategoryEvent e) {
        if (e.eventType() == null || e.categoryId() == null) {
            throw new IllegalArgumentException("Category event " + e.eventId() + " has no type or categoryId");
        }
        lockCategory(e.categoryId());
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", e.categoryId())
                .addValue("name", e.name())
                .addValue("parentId", e.parentId())
                .addValue("version", e.categoryVersion())
                .addValue("now", timestamp(clock.instant()));
        switch (e.eventType()) {
            case EventTypes.CATEGORY_CREATED, EventTypes.CATEGORY_UPDATED -> {
                if (blank(e.name())) {
                    throw new IllegalArgumentException("Category event " + e.eventId() + " has no name");
                }
                int rows = jdbc.update("""
                        INSERT INTO categories (id, name, parent_id, category_version, updated_at)
                        VALUES (:id, :name, :parentId, :version, :now)
                        ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, parent_id = EXCLUDED.parent_id,
                            category_version = EXCLUDED.category_version, updated_at = EXCLUDED.updated_at
                        WHERE categories.category_version < EXCLUDED.category_version
                        """, p);
                // Products show the category's current name (and are found by it), even if they arrived first
                jdbc.update("""
                        UPDATE search_products SET category_name = c.name, updated_at = :now
                        FROM categories c
                        WHERE c.id = :id AND search_products.category_id = c.id
                          AND search_products.category_name IS DISTINCT FROM c.name
                        """, p);
                addWords(QueryText.dictionaryWords(e.name()));
                return rows > 0;
            }
            case EventTypes.CATEGORY_DELETED -> {
                return jdbc.update("DELETE FROM categories WHERE id = :id AND category_version < :version", p) > 0;
            }
            default -> {
                return false;
            }
        }
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
                INSERT INTO search_products (product_id, available_quantity, inventory_version, updated_at)
                VALUES (:id, :available, :version, :now)
                ON CONFLICT (product_id) DO UPDATE SET available_quantity = EXCLUDED.available_quantity,
                    inventory_version = EXCLUDED.inventory_version, updated_at = EXCLUDED.updated_at
                WHERE search_products.inventory_version < EXCLUDED.inventory_version
                """, new MapSqlParameterSource()
                .addValue("id", e.productId())
                .addValue("available", e.availableQuantity())
                .addValue("version", e.inventoryVersion())
                .addValue("now", timestamp(clock.instant()))) > 0;
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
                INSERT INTO search_products (product_id, average_rating, review_count, rating_version, updated_at)
                VALUES (:id, :rating, :count, :version, :now)
                ON CONFLICT (product_id) DO UPDATE SET average_rating = EXCLUDED.average_rating,
                    review_count = EXCLUDED.review_count, rating_version = EXCLUDED.rating_version,
                    updated_at = EXCLUDED.updated_at
                WHERE search_products.rating_version < EXCLUDED.rating_version
                """, new MapSqlParameterSource()
                .addValue("id", e.productId())
                .addValue("rating", e.averageRating())
                .addValue("count", e.reviewCount())
                .addValue("version", e.ratingVersion())
                .addValue("now", timestamp(clock.instant()))) > 0;
    }

    /**
     * Product and category events come from different topics, so they are applied at the same time by different
     * threads. Without this lock a product could be saved while its category is being saved, each transaction not yet
     * seeing the other, and the product would keep no category name. With it (held until commit), whichever comes
     * second sees the first one's data.
     */
    private void lockCategory(UUID categoryId) {
        if (categoryId != null) {
            jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT pg_advisory_xact_lock(:key)) AS locked",
                    new MapSqlParameterSource("key", categoryId.getMostSignificantBits() ^ categoryId.getLeastSignificantBits()),
                    Long.class);
        }
    }

    private void addWords(Set<String> words) {
        if (words.isEmpty()) {
            return;
        }
        Collection<String> list = words.stream().map(w -> w.length() > 100 ? w.substring(0, 100) : w).toList();
        SqlParameterSource[] batch = list.stream()
                .map(w -> new MapSqlParameterSource("word", w)).toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("INSERT INTO search_words (word) VALUES (:word) ON CONFLICT (word) DO NOTHING", batch);
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

package com.ecommerce.recommendationservice.recommend;

import com.ecommerce.recommendationservice.config.RecommendationProperties;
import com.ecommerce.recommendationservice.dto.response.RecommendationStatsResponse;
import com.ecommerce.recommendationservice.dto.response.RecommendedProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The SQL behind recommendations. Only products that can be bought (ACTIVE, in stock) are ever returned. */
@Repository
@RequiredArgsConstructor
public class RecommendationRepository {

    /** What a recommendation may show. */
    static final String AVAILABLE = "p.status = 'ACTIVE' AND p.available_quantity > 0 AND p.name IS NOT NULL";
    private static final String CARD = """
            p.product_id, p.name, p.brand, p.image_url, p.selling_price, p.average_rating, p.review_count,
            p.available_quantity, p.category_id, c.name AS category_name""";
    private static final String FROM = " FROM products p LEFT JOIN categories c ON c.id = p.category_id ";
    private static final String SUBTREE = """
            (WITH RECURSIVE subtree(id) AS (SELECT CAST(:categoryId AS uuid)
                UNION SELECT k.id FROM categories k JOIN subtree s ON k.parent_id = s.id) SELECT id FROM subtree)""";

    private final NamedParameterJdbcTemplate jdbc;
    private final RecommendationProperties properties;

    /** A product's facts used to find similar ones. */
    record Base(UUID productId, String name, String brand, UUID categoryId, UUID parentCategoryId, BigDecimal sellingPrice) {
    }

    record Counted(RecommendedProduct product, long count) {
    }

    // ---------- Product page ----------

    public List<Counted> boughtTogether(UUID productId, int limit) {
        return jdbc.query("SELECT " + CARD + ", bt.orders AS n" + FROM
                        + "JOIN bought_together bt ON bt.other_product_id = p.product_id "
                        + "WHERE bt.product_id = :id AND bt.orders > 0 AND " + AVAILABLE
                        + " ORDER BY bt.orders DESC, p.average_rating DESC, p.review_count DESC, p.product_id LIMIT :limit",
                new MapSqlParameterSource("id", productId).addValue("limit", limit), counted());
    }

    public List<Counted> alsoViewed(UUID productId, int limit) {
        return jdbc.query("SELECT " + CARD + ", vt.customers AS n" + FROM
                        + "JOIN viewed_together vt ON vt.other_product_id = p.product_id "
                        + "WHERE vt.product_id = :id AND " + AVAILABLE
                        + " ORDER BY vt.customers DESC, p.average_rating DESC, p.review_count DESC, p.product_id LIMIT :limit",
                new MapSqlParameterSource("id", productId).addValue("limit", limit), counted());
    }

    public Optional<Base> base(UUID productId) {
        return jdbc.query("""
                SELECT p.product_id, p.name, p.brand, p.category_id, c.parent_id, p.selling_price
                FROM products p LEFT JOIN categories c ON c.id = p.category_id
                WHERE p.product_id = :id AND p.name IS NOT NULL
                """, new MapSqlParameterSource("id", productId), (rs, i) -> new Base(rs.getObject("product_id", UUID.class),
                rs.getString("name"), rs.getString("brand"), rs.getObject("category_id", UUID.class),
                rs.getObject("parent_id", UUID.class), rs.getBigDecimal("selling_price"))).stream().findFirst();
    }

    /**
     * Same category first (3 points), else a related one - parent, child or sibling (1 point); +1 for a similar price
     * (60 % - 160 %), +1 for the same brand. Ties: better rated, more reviewed.
     */
    public List<Counted> similar(Base base, int limit) {
        if (base.categoryId() == null) {
            return List.of();
        }
        MapSqlParameterSource p = new MapSqlParameterSource("id", base.productId()).addValue("cat", base.categoryId())
                .addValue("limit", limit);
        StringBuilder related = new StringBuilder("(p.category_id = :cat OR c.parent_id = :cat");
        if (base.parentCategoryId() != null) {
            p.addValue("parent", base.parentCategoryId());
            related.append(" OR p.category_id = :parent OR c.parent_id = :parent");
        }
        related.append(")");
        StringBuilder score = new StringBuilder("(CASE WHEN p.category_id = :cat THEN 3 ELSE 1 END");
        if (base.sellingPrice() != null) {
            p.addValue("low", base.sellingPrice().multiply(new BigDecimal("0.6")))
                    .addValue("high", base.sellingPrice().multiply(new BigDecimal("1.6")));
            score.append(" + CASE WHEN p.selling_price BETWEEN :low AND :high THEN 1 ELSE 0 END");
        }
        if (base.brand() != null) {
            p.addValue("brand", base.brand().toLowerCase());
            score.append(" + CASE WHEN lower(p.brand) = :brand THEN 1 ELSE 0 END");
        }
        score.append(")");
        return jdbc.query("SELECT " + CARD + ", " + score + " AS n" + FROM + "WHERE p.product_id <> :id AND " + AVAILABLE
                + " AND " + related + " ORDER BY n DESC, p.average_rating DESC, p.review_count DESC, p.product_id LIMIT :limit",
                p, counted());
    }

    // ---------- Trending ----------

    /** Most units sold since the given day (optionally in a category and its sub-categories). */
    public List<Counted> bestsellers(UUID categoryId, LocalDate since, Collection<UUID> exclude, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("since", Date.valueOf(since)).addValue("limit", limit);
        String where = "ds.sale_date >= :since AND " + AVAILABLE + inCategory(categoryId, p) + excluding(exclude, p);
        return jdbc.query("SELECT " + CARD + ", SUM(ds.units) AS n" + FROM
                + "JOIN daily_sales ds ON ds.product_id = p.product_id WHERE " + where
                + " GROUP BY p.product_id, c.name HAVING SUM(ds.units) > 0"
                + " ORDER BY n DESC, p.average_rating DESC, p.review_count DESC, p.product_id LIMIT :limit", p, counted());
    }

    /** Best rated (then most reviewed) - fills lists when there are few sales or signals. */
    public List<Counted> topRated(UUID categoryId, Collection<UUID> exclude, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("limit", limit);
        String where = AVAILABLE + inCategory(categoryId, p) + excluding(exclude, p);
        return jdbc.query("SELECT " + CARD + ", p.review_count AS n" + FROM + "WHERE " + where
                + " ORDER BY p.average_rating DESC, p.review_count DESC, p.product_id LIMIT :limit", p, counted());
    }

    public Optional<String> categoryName(UUID categoryId) {
        return jdbc.queryForList("SELECT name FROM categories WHERE id = :id", new MapSqlParameterSource("id", categoryId),
                String.class).stream().findFirst();
    }

    // ---------- A customer's history ----------

    /** Products from the customer's paid (not cancelled) orders, most recent first. */
    public Map<UUID, String> purchased(UUID userId, int limit) {
        return names("""
                SELECT oi.product_id, MAX(o.confirmed_at) AS at FROM order_items oi JOIN orders o ON o.order_id = oi.order_id
                WHERE o.user_id = :userId AND o.cancelled_at IS NULL GROUP BY oi.product_id ORDER BY at DESC LIMIT :limit
                """, userId, limit);
    }

    /** The customer's most recently viewed products. */
    public Map<UUID, String> viewed(UUID userId, int limit) {
        return names("""
                SELECT v.product_id, v.last_viewed_at AS at FROM product_views v WHERE v.user_id = :userId
                ORDER BY v.last_viewed_at DESC LIMIT :limit
                """, userId, limit);
    }

    public List<RecommendedProduct> recentlyViewed(UUID userId, int limit) {
        return jdbc.query("SELECT " + CARD + ", 0 AS n" + FROM + "JOIN product_views v ON v.product_id = p.product_id "
                        + "WHERE v.user_id = :userId AND p.name IS NOT NULL AND p.status = 'ACTIVE' "
                        + "ORDER BY v.last_viewed_at DESC LIMIT :limit",
                new MapSqlParameterSource("userId", userId).addValue("limit", limit), counted())
                .stream().map(Counted::product).toList();
    }

    public List<Signal> boughtWith(Map<UUID, String> sources) {
        return pairSignals("bought_together", "orders", "bt.orders > 0", sources, Signal.Kind.BOUGHT_WITH);
    }

    public List<Signal> viewedWith(Map<UUID, String> sources) {
        return pairSignals("viewed_together", "customers", "bt.customers > 0", sources, Signal.Kind.VIEWED_WITH);
    }

    /** Well-rated products in the same categories as the sources. */
    public List<Signal> similarTo(Map<UUID, String> sources) {
        if (sources.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT p.product_id, s.product_id AS source, p.average_rating AS strength
                FROM products s JOIN products p ON p.category_id = s.category_id AND p.product_id <> s.product_id
                WHERE s.product_id IN (:sources) AND\s""" + AVAILABLE + """
                 ORDER BY p.average_rating DESC, p.review_count DESC LIMIT 200
                """, new MapSqlParameterSource("sources", sources.keySet()),
                (rs, i) -> {
                    UUID source = rs.getObject("source", UUID.class);
                    return new Signal(rs.getObject("product_id", UUID.class), Signal.Kind.SIMILAR_TO, source,
                            sources.get(source), rs.getDouble("strength"));
                });
    }

    /** Bestsellers in the categories of the sources ("Popular in Phones"). */
    public List<Signal> popularIn(Map<UUID, String> sources, LocalDate since) {
        if (sources.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT p.product_id, c.id AS category_id, c.name AS category_name, SUM(ds.units) AS units
                FROM daily_sales ds JOIN products p ON p.product_id = ds.product_id JOIN categories c ON c.id = p.category_id
                WHERE ds.sale_date >= :since AND p.category_id IN (SELECT category_id FROM products WHERE product_id IN (:sources))
                  AND\s""" + AVAILABLE + """
                 GROUP BY p.product_id, c.id, c.name HAVING SUM(ds.units) > 0 ORDER BY units DESC LIMIT 100
                """, new MapSqlParameterSource("sources", sources.keySet()).addValue("since", Date.valueOf(since)),
                (rs, i) -> new Signal(rs.getObject("product_id", UUID.class), Signal.Kind.POPULAR_IN,
                        rs.getObject("category_id", UUID.class), rs.getString("category_name"), rs.getDouble("units")));
    }

    /** Product cards for these ids, in the given order (unavailable ones dropped). */
    public List<RecommendedProduct> cards(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, RecommendedProduct> byId = new LinkedHashMap<>();
        jdbc.query("SELECT " + CARD + ", 0 AS n" + FROM + "WHERE p.product_id IN (:ids) AND " + AVAILABLE,
                new MapSqlParameterSource("ids", ids), counted()).forEach(c -> byId.put(c.product().productId(), c.product()));
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    // ---------- Admin ----------

    public RecommendationStatsResponse stats() {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT (SELECT COUNT(*) FROM products) AS products,
                       (SELECT COUNT(*) FROM products p WHERE\s""" + AVAILABLE + """
                ) AS recommendable,
                       (SELECT COUNT(*) FROM categories) AS categories,
                       (SELECT COUNT(*) FROM orders WHERE cancelled_at IS NULL) AS paid,
                       (SELECT COUNT(*) FROM orders WHERE cancelled_at IS NOT NULL) AS cancelled,
                       (SELECT COUNT(*) FROM bought_together WHERE orders > 0) / 2 AS bought_pairs,
                       (SELECT COUNT(DISTINCT user_id) FROM product_views) AS viewers,
                       (SELECT COUNT(*) FROM viewed_together) / 2 AS viewed_pairs
                """, new MapSqlParameterSource());
        return new RecommendationStatsResponse(n(row, "products"), n(row, "recommendable"), n(row, "categories"),
                n(row, "paid"), n(row, "cancelled"), n(row, "bought_pairs"), n(row, "viewers"), n(row, "viewed_pairs"));
    }

    // ---------- Helpers ----------

    private List<Signal> pairSignals(String table, String column, String positive, Map<UUID, String> sources, Signal.Kind kind) {
        if (sources.isEmpty()) {
            return List.of();
        }
        return jdbc.query("SELECT bt.other_product_id, bt.product_id AS source, bt." + column + " AS strength FROM " + table
                        + " bt JOIN products p ON p.product_id = bt.other_product_id WHERE bt.product_id IN (:sources) AND "
                        + positive + " AND " + AVAILABLE + " ORDER BY bt." + column + " DESC LIMIT 200",
                new MapSqlParameterSource("sources", sources.keySet()),
                (rs, i) -> {
                    UUID source = rs.getObject("source", UUID.class);
                    return new Signal(rs.getObject("other_product_id", UUID.class), kind, source, sources.get(source),
                            rs.getDouble("strength"));
                });
    }

    private Map<UUID, String> names(String idsSql, UUID userId, int limit) {
        Map<UUID, String> result = new LinkedHashMap<>();
        jdbc.query("SELECT h.product_id, p.name FROM (" + idsSql + ") h JOIN products p ON p.product_id = h.product_id "
                        + "WHERE p.name IS NOT NULL ORDER BY h.at DESC",
                new MapSqlParameterSource("userId", userId).addValue("limit", limit),
                rs -> {
                    result.put(rs.getObject("product_id", UUID.class), rs.getString("name"));
                });
        return result;
    }

    private static String inCategory(UUID categoryId, MapSqlParameterSource p) {
        if (categoryId == null) {
            return "";
        }
        p.addValue("categoryId", categoryId);
        return " AND p.category_id IN " + SUBTREE;
    }

    private static String excluding(Collection<UUID> exclude, MapSqlParameterSource p) {
        if (exclude == null || exclude.isEmpty()) {
            return "";
        }
        p.addValue("exclude", exclude);
        return " AND p.product_id NOT IN (:exclude)";
    }

    private RowMapper<Counted> counted() {
        return (rs, i) -> new Counted(new RecommendedProduct(rs.getObject("product_id", UUID.class), rs.getString("name"),
                rs.getString("brand"), rs.getString("image_url"), rs.getBigDecimal("selling_price"), properties.currency(),
                rs.getBigDecimal("average_rating"), rs.getInt("review_count"), rs.getInt("available_quantity") > 0,
                rs.getObject("category_id", UUID.class), rs.getString("category_name"), null), rs.getLong("n"));
    }

    private static long n(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}

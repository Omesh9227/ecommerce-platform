package com.ecommerce.searchservice.search;

import com.ecommerce.searchservice.config.SearchProperties;
import com.ecommerce.searchservice.dto.response.Facets;
import com.ecommerce.searchservice.dto.response.IndexStatsResponse;
import com.ecommerce.searchservice.dto.response.PopularSearch;
import com.ecommerce.searchservice.dto.response.SearchHit;
import com.ecommerce.searchservice.dto.response.SuggestResponse;
import com.ecommerce.searchservice.search.SearchFilter.Facet;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** The SQL behind searching. All user input goes in as bind parameters; only fixed SQL fragments are concatenated. */
@Repository
@RequiredArgsConstructor
public class SearchRepository {

    private static final int[] RATING_STEPS = {4, 3, 2, 1};

    private final NamedParameterJdbcTemplate jdbc;
    private final SearchProperties properties;

    // ---------- Results ----------

    public long count(SearchCriteria c, List<String> words) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = SearchFilter.where(c, words, null, params);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM search_products p WHERE " + where, params, Long.class);
        return n == null ? 0 : n;
    }

    public List<SearchHit> page(SearchCriteria c, List<String> words) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", c.size())
                .addValue("offset", (long) c.page() * c.size());
        String where = SearchFilter.where(c, words, null, params);
        boolean hasText = !words.isEmpty();
        String rank = hasText ? SearchFilter.rank(words, params) : "0";
        String sql = "SELECT p.* FROM search_products p WHERE " + where
                + " ORDER BY " + SearchFilter.orderBy(c.sort(), hasText, rank)
                + " LIMIT :limit OFFSET :offset";
        return jdbc.query(sql, params, hitMapper());
    }

    // ---------- Facets ----------

    public Facets facets(SearchCriteria c, List<String> words) {
        return new Facets(brands(c, words), categories(c, words), priceRanges(c, words), ratings(c, words), availability(c, words));
    }

    private List<Facets.Value> brands(SearchCriteria c, List<String> words) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", properties.facetLimit());
        String where = SearchFilter.where(c, words, Facet.BRAND, params);
        return jdbc.query("SELECT min(p.brand) AS brand, COUNT(*) AS n FROM search_products p WHERE " + where
                        + " AND p.brand IS NOT NULL GROUP BY lower(p.brand) ORDER BY n DESC, brand LIMIT :limit",
                params, (rs, i) -> new Facets.Value(rs.getString("brand"), rs.getLong("n")));
    }

    private List<Facets.CategoryCount> categories(SearchCriteria c, List<String> words) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", properties.facetLimit());
        String where = SearchFilter.where(c, words, Facet.CATEGORY, params);
        return jdbc.query("SELECT p.category_id, min(p.category_name) AS name, COUNT(*) AS n FROM search_products p WHERE "
                        + where + " AND p.category_id IS NOT NULL GROUP BY p.category_id ORDER BY n DESC, name LIMIT :limit",
                params, (rs, i) -> new Facets.CategoryCount(rs.getObject("category_id", UUID.class), rs.getString("name"),
                        rs.getLong("n")));
    }

    private List<Facets.PriceRange> priceRanges(SearchCriteria c, List<String> words) {
        List<BigDecimal> bounds = properties.priceRanges();
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder select = new StringBuilder("SELECT ");
        for (int i = 0; i <= bounds.size(); i++) {
            BigDecimal min = i == 0 ? null : bounds.get(i - 1);
            BigDecimal max = i == bounds.size() ? null : bounds.get(i);
            if (min != null) {
                params.addValue("min" + i, min);
            }
            if (max != null) {
                params.addValue("max" + i, max);
            }
            String condition = (min == null ? "TRUE" : "p.selling_price >= :min" + i)
                    + " AND " + (max == null ? "TRUE" : "p.selling_price < :max" + i);
            select.append(i == 0 ? "" : ", ").append("COUNT(*) FILTER (WHERE ").append(condition).append(") AS r").append(i);
        }
        String where = SearchFilter.where(c, words, Facet.PRICE, params);
        Map<String, Object> row = jdbc.queryForMap(select + " FROM search_products p WHERE " + where
                + " AND p.selling_price IS NOT NULL", params);
        List<Facets.PriceRange> ranges = new ArrayList<>();
        for (int i = 0; i <= bounds.size(); i++) {
            BigDecimal min = i == 0 ? BigDecimal.ZERO : bounds.get(i - 1);
            BigDecimal max = i == bounds.size() ? null : bounds.get(i);
            String label = i == 0 ? "Under " + money(max)
                    : max == null ? money(min) + " & above" : money(min) + " - " + money(max);
            ranges.add(new Facets.PriceRange(label, min, max, ((Number) row.get("r" + i)).longValue()));
        }
        return ranges;
    }

    private List<Facets.Rating> ratings(SearchCriteria c, List<String> words) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = SearchFilter.where(c, words, Facet.RATING, params);
        StringBuilder select = new StringBuilder("SELECT ");
        for (int i = 0; i < RATING_STEPS.length; i++) {
            select.append(i == 0 ? "" : ", ").append("COUNT(*) FILTER (WHERE p.average_rating >= ").append(RATING_STEPS[i])
                    .append(") AS s").append(RATING_STEPS[i]);
        }
        Map<String, Object> row = jdbc.queryForMap(select + " FROM search_products p WHERE " + where, params);
        List<Facets.Rating> ratings = new ArrayList<>();
        for (int stars : RATING_STEPS) {
            ratings.add(new Facets.Rating(stars, stars + (stars == 1 ? " star" : " stars") + " & up",
                    ((Number) row.get("s" + stars)).longValue()));
        }
        return ratings;
    }

    private Facets.Availability availability(SearchCriteria c, List<String> words) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = SearchFilter.where(c, words, Facet.STOCK, params);
        Map<String, Object> row = jdbc.queryForMap("SELECT COUNT(*) FILTER (WHERE p.available_quantity > 0) AS yes, "
                + "COUNT(*) FILTER (WHERE p.available_quantity = 0) AS no FROM search_products p WHERE " + where, params);
        return new Facets.Availability(((Number) row.get("yes")).longValue(), ((Number) row.get("no")).longValue());
    }

    // ---------- Suggestions ----------

    public List<SuggestResponse.Product> suggestProducts(List<String> words, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", limit);
        SearchCriteria none = new SearchCriteria(null, null, List.of(), null, null, null, null, SortOrder.RELEVANCE, 0, limit);
        String where = SearchFilter.where(none, words, null, params);
        String rank = SearchFilter.rank(words, params);
        return jdbc.query("SELECT p.product_id, p.name, p.brand, p.image_url, p.selling_price FROM search_products p WHERE "
                        + where + " ORDER BY " + rank + " DESC, p.review_count DESC, p.name LIMIT :limit",
                params, (rs, i) -> new SuggestResponse.Product(rs.getObject("product_id", UUID.class), rs.getString("name"),
                        rs.getString("brand"), rs.getString("image_url"), rs.getBigDecimal("selling_price")));
    }

    /** Brands whose name starts with the text, most products first. */
    public List<String> suggestBrands(String prefix, int limit) {
        return jdbc.queryForList("""
                SELECT min(brand) FROM search_products
                WHERE status = 'ACTIVE' AND lower(brand) LIKE :prefix
                GROUP BY lower(brand) ORDER BY COUNT(*) DESC, min(brand) LIMIT :limit
                """, new MapSqlParameterSource("prefix", likePrefix(prefix)).addValue("limit", limit), String.class);
    }

    /** Categories with a word starting with the text ("pho" -> Phones, Mobile Phones). */
    public List<SuggestResponse.Category> suggestCategories(String prefix, int limit) {
        return jdbc.query("""
                SELECT id, name FROM categories
                WHERE lower(name) LIKE :prefix OR lower(name) LIKE :wordPrefix
                ORDER BY name LIMIT :limit
                """, new MapSqlParameterSource("prefix", likePrefix(prefix)).addValue("wordPrefix", "% " + likePrefix(prefix))
                        .addValue("limit", limit),
                (rs, i) -> new SuggestResponse.Category(rs.getObject("id", UUID.class), rs.getString("name")));
    }

    public List<String> suggestSearches(String prefix, Instant since, int limit) {
        return jdbc.queryForList("""
                SELECT term FROM search_terms WHERE term LIKE :prefix AND last_searched_at >= :since
                ORDER BY search_count DESC, term LIMIT :limit
                """, new MapSqlParameterSource("prefix", likePrefix(prefix)).addValue("since", Timestamp.from(since))
                .addValue("limit", limit), String.class);
    }

    // ---------- Popular searches ----------

    public void recordSearch(String term, Instant now) {
        jdbc.update("""
                INSERT INTO search_terms (term, search_count, last_searched_at) VALUES (:term, 1, :now)
                ON CONFLICT (term) DO UPDATE SET search_count = search_terms.search_count + 1,
                    last_searched_at = EXCLUDED.last_searched_at
                """, new MapSqlParameterSource("term", term).addValue("now", Timestamp.from(now)));
    }

    public List<PopularSearch> popular(Instant since, int limit) {
        return jdbc.query("""
                SELECT term, search_count FROM search_terms WHERE last_searched_at >= :since
                ORDER BY search_count DESC, last_searched_at DESC LIMIT :limit
                """, new MapSqlParameterSource("since", Timestamp.from(since)).addValue("limit", limit),
                (rs, i) -> new PopularSearch(rs.getString("term"), rs.getLong("search_count")));
    }

    // ---------- Admin ----------

    public IndexStatsResponse stats() {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT (SELECT COUNT(*) FROM search_products) AS products,
                       (SELECT COUNT(*) FROM search_products WHERE status = 'ACTIVE' AND name IS NOT NULL) AS searchable,
                       (SELECT COUNT(*) FROM search_products WHERE name IS NOT NULL AND category_id IS NULL) AS without_details,
                       (SELECT COUNT(*) FROM categories) AS categories,
                       (SELECT COUNT(*) FROM search_words) AS words,
                       (SELECT COUNT(*) FROM search_terms) AS terms
                """, new MapSqlParameterSource());
        return new IndexStatsResponse(n(row, "products"), n(row, "searchable"), n(row, "without_details"),
                n(row, "categories"), n(row, "words"), n(row, "terms"));
    }

    // ---------- Helpers ----------

    private RowMapper<SearchHit> hitMapper() {
        return (rs, i) -> {
            int available = rs.getInt("available_quantity");
            return new SearchHit(rs.getObject("product_id", UUID.class), rs.getString("sku"), rs.getString("name"),
                    rs.getString("brand"), rs.getString("image_url"), rs.getBigDecimal("price"),
                    rs.getBigDecimal("selling_price"), rs.getBigDecimal("discount_percent"), properties.currency(),
                    rs.getBigDecimal("average_rating"), rs.getInt("review_count"), available > 0, available,
                    rs.getObject("category_id", UUID.class), rs.getString("category_name"));
        };
    }

    private String money(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return properties.currency() + " " + format.format(amount);
    }

    private static String likePrefix(String text) {
        return text.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static long n(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}

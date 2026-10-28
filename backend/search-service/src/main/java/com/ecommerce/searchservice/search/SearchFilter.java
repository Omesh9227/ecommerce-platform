package com.ecommerce.searchservice.search;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;
import java.util.Locale;

/**
 * Builds the WHERE clause of a search. Facets use "disjunctive" counting: each facet is counted with every filter
 * except its own, so choosing the brand "Google" still shows how many Samsung products there are.
 */
final class SearchFilter {

    enum Facet { BRAND, CATEGORY, PRICE, RATING, STOCK }

    private SearchFilter() {
    }

    /** Text match: every word (as a prefix) must match; an all-stop-words query ("the") matches everything. */
    static final String TEXT_MATCH = "(numnode(to_tsquery('english', :tsq)) = 0 OR p.search_vector @@ to_tsquery('english', :tsq))";

    static String where(SearchCriteria c, List<String> words, Facet without, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder("p.status = 'ACTIVE' AND p.name IS NOT NULL");
        String tsq = QueryText.tsQuery(words);
        if (tsq != null) {
            params.addValue("tsq", tsq);
            sql.append(" AND ").append(TEXT_MATCH);
        }
        if (c.categoryId() != null && without != Facet.CATEGORY) {
            params.addValue("categoryId", c.categoryId());
            sql.append(" AND p.category_id IN (WITH RECURSIVE subtree(id) AS ("
                    + "SELECT CAST(:categoryId AS uuid) "
                    + "UNION SELECT c.id FROM categories c JOIN subtree s ON c.parent_id = s.id) "
                    + "SELECT id FROM subtree)");
        }
        if (c.brands() != null && !c.brands().isEmpty() && without != Facet.BRAND) {
            params.addValue("brands", c.brands().stream().map(b -> b.trim().toLowerCase(Locale.ROOT)).toList());
            sql.append(" AND lower(p.brand) IN (:brands)");
        }
        if (without != Facet.PRICE) {
            if (c.minPrice() != null) {
                params.addValue("minPrice", c.minPrice());
                sql.append(" AND p.selling_price >= :minPrice");
            }
            if (c.maxPrice() != null) {
                params.addValue("maxPrice", c.maxPrice());
                sql.append(" AND p.selling_price <= :maxPrice");
            }
        }
        if (c.minRating() != null && without != Facet.RATING) {
            params.addValue("minRating", c.minRating());
            sql.append(" AND p.average_rating >= :minRating");
        }
        if (c.inStock() != null && without != Facet.STOCK) {
            sql.append(c.inStock() ? " AND p.available_quantity > 0" : " AND p.available_quantity = 0");
        }
        return sql.toString();
    }

    /** Relevance score: full-text rank (name/brand weigh most), plus a boost for exact and starts-with name matches. */
    static String rank(List<String> words, MapSqlParameterSource params) {
        params.addValue("phrase", QueryText.normalized(words));
        params.addValue("phrasePrefix", QueryText.normalized(words).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        return "(ts_rank(p.search_vector, to_tsquery('english', :tsq))"
                + " + CASE WHEN lower(p.name) = :phrase THEN 1 ELSE 0 END"
                + " + CASE WHEN lower(p.name) LIKE :phrasePrefix THEN 0.5 ELSE 0 END)";
    }

    static String orderBy(SortOrder sort, boolean hasText, String rank) {
        return switch (sort) {
            case RELEVANCE -> hasText
                    ? "(p.available_quantity > 0) DESC, " + rank + " DESC, p.review_count DESC, p.product_id"
                    : "(p.available_quantity > 0) DESC, p.average_rating DESC, p.review_count DESC, p.listed_at DESC NULLS LAST, p.product_id";
            case PRICE_ASC -> "p.selling_price ASC NULLS LAST, p.product_id";
            case PRICE_DESC -> "p.selling_price DESC NULLS LAST, p.product_id";
            case RATING -> "p.average_rating DESC, p.review_count DESC, p.product_id";
            case NEWEST -> "p.listed_at DESC NULLS LAST, p.product_id";
        };
    }
}

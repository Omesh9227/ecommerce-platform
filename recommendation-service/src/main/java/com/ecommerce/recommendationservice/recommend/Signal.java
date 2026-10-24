package com.ecommerce.recommendationservice.recommend;

import java.util.UUID;

/**
 * One reason to recommend a product: "bought together with X 3 times", "popular in Phones".
 *
 * @param source the product (or category) it comes from, named in the reason
 */
record Signal(UUID productId, Kind kind, UUID source, String sourceName, double strength) {

    enum Kind {
        BOUGHT_WITH, VIEWED_WITH, SIMILAR_TO, POPULAR_IN
    }
}

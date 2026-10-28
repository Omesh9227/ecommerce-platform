package com.ecommerce.searchservice.dto.response;

/**
 * @param products   rows in the index (incl. discontinued and products only known from stock/rating events so far)
 * @param searchable ACTIVE products that can be found
 * @param withoutDetails products with no brand/category yet: run product-service's republish to fill them in
 */
public record IndexStatsResponse(long products, long searchable, long withoutDetails, long categories,
                                 long dictionaryWords, long searchTerms) {
}

package com.ecommerce.recommendationservice.dto.response;

/**
 * @param recommendable  ACTIVE, in stock, with details: the products that can be shown
 * @param paidOrders     paid orders counted (cancelled ones excluded)
 * @param boughtTogetherPairs product pairs bought together at least once
 * @param viewedTogetherPairs product pairs viewed by the same customer
 */
public record RecommendationStatsResponse(long products, long recommendable, long categories, long paidOrders,
                                          long cancelledOrders, long boughtTogetherPairs, long customersWithViews,
                                          long viewedTogetherPairs) {
}

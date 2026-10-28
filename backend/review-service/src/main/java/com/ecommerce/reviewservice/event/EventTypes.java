package com.ecommerce.reviewservice.event;

/** Event types this service reads (order-events) and writes (review-events). */
public final class EventTypes {

    // order-events (order-service): only deliveries matter here
    public static final String ORDER_DELIVERED = "ORDER_DELIVERED";

    // review-events (this service)
    public static final String PRODUCT_RATING_UPDATED = "PRODUCT_RATING_UPDATED";

    private EventTypes() {
    }
}

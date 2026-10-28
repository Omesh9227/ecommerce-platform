package com.ecommerce.wishlistservice.event;

/** Event types this service reads (product-events) and writes (wishlist-events). */
public final class EventTypes {

    // product-events (product-service)
    public static final String PRODUCT_CREATED = "PRODUCT_CREATED";
    public static final String PRODUCT_UPDATED = "PRODUCT_UPDATED";
    public static final String PRODUCT_DISCONTINUED = "PRODUCT_DISCONTINUED";

    // wishlist-events (this service)
    public static final String WISHLIST_PRICE_DROPPED = "WISHLIST_PRICE_DROPPED";

    private EventTypes() {
    }
}

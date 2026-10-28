package com.ecommerce.productservice.event;

/**
 * Event type names on the wire. Strings rather than an enum, so an unknown future event type
 * is simply ignored by consumers instead of failing deserialisation.
 */
public final class EventTypes {

    // Published by product-service on the product-events topic
    public static final String PRODUCT_CREATED = "PRODUCT_CREATED";
    public static final String PRODUCT_UPDATED = "PRODUCT_UPDATED";
    public static final String PRODUCT_DISCONTINUED = "PRODUCT_DISCONTINUED";

    public static final String CATEGORY_CREATED = "CATEGORY_CREATED";
    public static final String CATEGORY_UPDATED = "CATEGORY_UPDATED";
    public static final String CATEGORY_DELETED = "CATEGORY_DELETED";

    // Published by inventory-service on the inventory-events topic
    public static final String INVENTORY_UPDATED = "INVENTORY_UPDATED";
    public static final String INVENTORY_LOW = "INVENTORY_LOW";

    // Published by review-service on the review-events topic
    public static final String PRODUCT_RATING_UPDATED = "PRODUCT_RATING_UPDATED";

    private EventTypes() {
    }
}

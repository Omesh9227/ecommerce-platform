package com.ecommerce.searchservice.event;

import java.util.Set;

/** Event types search-service applies. Anything else is ignored. */
public final class EventTypes {

    // product-events and category-events (product-service)
    public static final String PRODUCT_CREATED = "PRODUCT_CREATED";
    public static final String PRODUCT_UPDATED = "PRODUCT_UPDATED";
    public static final String PRODUCT_DISCONTINUED = "PRODUCT_DISCONTINUED";
    public static final String CATEGORY_CREATED = "CATEGORY_CREATED";
    public static final String CATEGORY_UPDATED = "CATEGORY_UPDATED";
    public static final String CATEGORY_DELETED = "CATEGORY_DELETED";

    // inventory-events (inventory-service): both carry the current stock
    public static final String INVENTORY_UPDATED = "INVENTORY_UPDATED";
    public static final String INVENTORY_LOW = "INVENTORY_LOW";

    // review-events (review-service)
    public static final String PRODUCT_RATING_UPDATED = "PRODUCT_RATING_UPDATED";

    public static final Set<String> PRODUCT = Set.of(PRODUCT_CREATED, PRODUCT_UPDATED, PRODUCT_DISCONTINUED);
    public static final Set<String> STOCK = Set.of(INVENTORY_UPDATED, INVENTORY_LOW);

    private EventTypes() {
    }
}

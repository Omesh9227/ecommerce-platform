package com.ecommerce.recommendationservice.event;

import java.util.Set;

/** Event types recommendation-service applies. Anything else is ignored. */
public final class EventTypes {

    public static final String PRODUCT_CREATED = "PRODUCT_CREATED";
    public static final String PRODUCT_UPDATED = "PRODUCT_UPDATED";
    public static final String PRODUCT_DISCONTINUED = "PRODUCT_DISCONTINUED";
    public static final String CATEGORY_CREATED = "CATEGORY_CREATED";
    public static final String CATEGORY_UPDATED = "CATEGORY_UPDATED";
    public static final String CATEGORY_DELETED = "CATEGORY_DELETED";
    public static final String INVENTORY_UPDATED = "INVENTORY_UPDATED";
    public static final String INVENTORY_LOW = "INVENTORY_LOW";
    public static final String PRODUCT_RATING_UPDATED = "PRODUCT_RATING_UPDATED";

    /** Paid: the order counts as a purchase from now on. */
    public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";
    /** If it had been paid, the purchase is taken back out of the counts. */
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

    public static final Set<String> PRODUCT = Set.of(PRODUCT_CREATED, PRODUCT_UPDATED, PRODUCT_DISCONTINUED);
    public static final Set<String> STOCK = Set.of(INVENTORY_UPDATED, INVENTORY_LOW);

    private EventTypes() {
    }
}

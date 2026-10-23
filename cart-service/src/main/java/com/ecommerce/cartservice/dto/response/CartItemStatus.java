package com.ecommerce.cartservice.dto.response;

/** Whether a cart line can be bought right now. */
public enum CartItemStatus {
    /** For sale and enough stock for the quantity in the cart. */
    AVAILABLE,
    /** For sale, but fewer units in stock than the quantity in the cart. */
    INSUFFICIENT_STOCK,
    /** For sale, but no stock at all. */
    OUT_OF_STOCK,
    /** Not for sale any more (inactive, discontinued or deleted). */
    UNAVAILABLE,
    /** product-service could not be reached, so price and stock are from when the item was added. */
    UNKNOWN
}

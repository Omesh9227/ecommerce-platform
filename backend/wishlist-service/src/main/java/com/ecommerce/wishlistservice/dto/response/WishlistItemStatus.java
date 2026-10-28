package com.ecommerce.wishlistservice.dto.response;

/** Can the saved product be bought right now? */
public enum WishlistItemStatus {
    /** For sale and in stock */
    AVAILABLE,
    /** For sale, but no stock right now (it stays saved) */
    OUT_OF_STOCK,
    /** Not sold any more (shown with the name and price from when it was saved) */
    UNAVAILABLE,
    /** product-service is down: shown from the saved snapshot */
    UNKNOWN
}

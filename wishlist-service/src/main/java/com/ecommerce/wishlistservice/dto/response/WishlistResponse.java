package com.ecommerce.wishlistservice.dto.response;

import java.util.List;

/**
 * The caller's wishlist, newest first.
 *
 * @param pricesUpToDate false when product-service couldn't be reached (prices and names are from when items were saved)
 */
public record WishlistResponse(
        List<WishlistItemResponse> items,
        int totalItems,
        int maxItems,
        String currency,
        boolean pricesUpToDate,
        List<String> warnings
) {
}

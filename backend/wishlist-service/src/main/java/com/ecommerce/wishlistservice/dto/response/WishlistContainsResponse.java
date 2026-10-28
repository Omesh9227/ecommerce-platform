package com.ecommerce.wishlistservice.dto.response;

import java.util.List;
import java.util.UUID;

/** Which of the asked products are in the caller's wishlist (to fill the "heart" on product pages). */
public record WishlistContainsResponse(List<UUID> productIds) {
}

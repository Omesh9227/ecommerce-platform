package com.ecommerce.cartservice.service;

import com.ecommerce.cartservice.dto.request.AddItemRequest;
import com.ecommerce.cartservice.dto.request.UpdateQuantityRequest;
import com.ecommerce.cartservice.dto.response.CartResponse;

import java.util.UUID;

/** The cart of one user (always the caller - there is no way to address someone else's cart). */
public interface CartService {

    /** The cart with live prices and stock. Still works (with a warning) if product-service is down. */
    CartResponse getCart(UUID userId);

    /** Adds units of a product (or increases them). Needs product-service: 503 if it is down. */
    CartResponse addItem(UUID userId, AddItemRequest request);

    /** Changes the quantity of a product already in the cart. */
    CartResponse updateQuantity(UUID userId, UUID productId, UpdateQuantityRequest request);

    /** Removes a product. Removing one that isn't there is not an error. */
    CartResponse removeItem(UUID userId, UUID productId);

    void clear(UUID userId);
}

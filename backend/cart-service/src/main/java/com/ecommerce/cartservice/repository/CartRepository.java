package com.ecommerce.cartservice.repository;

import com.ecommerce.cartservice.model.CartLine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage for carts. One cart per user; every change also restarts the cart's expiry clock. */
public interface CartRepository {

    enum AddOutcome { ADDED, CART_FULL, QUANTITY_LIMIT }

    /** @param quantity the product's new quantity in the cart (only meaningful when ADDED) */
    record AddResult(AddOutcome outcome, int quantity) {
    }

    /** All lines of the user's cart (empty if there is none). Also restarts the expiry clock. */
    List<CartLine> findAll(UUID userId);

    Optional<CartLine> find(UUID userId, UUID productId);

    /**
     * Adds {@code quantityToAdd} units atomically. The snapshot in {@code line} replaces the stored one
     * (its quantity is ignored); the original addedAt is kept.
     *
     * @param maxQuantity the most units of this product the cart may hold after the add
     * @param maxItems    the most different products the cart may hold
     */
    AddResult add(UUID userId, CartLine line, int quantityToAdd, int maxQuantity, int maxItems);

    /** @return false if the product is not in the cart */
    boolean setQuantity(UUID userId, UUID productId, int quantity);

    /** @return false if the product was not in the cart */
    boolean remove(UUID userId, UUID productId);

    void clear(UUID userId);
}

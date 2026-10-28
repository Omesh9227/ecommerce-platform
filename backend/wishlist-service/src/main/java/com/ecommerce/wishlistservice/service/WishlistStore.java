package com.ecommerce.wishlistservice.service;

import com.ecommerce.wishlistservice.client.CatalogProduct;
import com.ecommerce.wishlistservice.entity.WishlistItem;
import com.ecommerce.wishlistservice.exception.BadRequestException;
import com.ecommerce.wishlistservice.repository.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The database part of "add to wishlist", in one short transaction (the product-service call happens before it).
 * Changes to one customer's wishlist are serialised with a lock, so two quick clicks can't save a product twice
 * or go past the limit.
 */
@Component
@RequiredArgsConstructor
public class WishlistStore {

    public record AddResult(WishlistItem item, boolean created) {
    }

    private final WishlistItemRepository repository;

    @Transactional
    public AddResult add(UUID userId, String email, CatalogProduct product, int maxItems, Instant now) {
        repository.lockWishlistOf(lockKey(userId));

        Optional<WishlistItem> existing = repository.findByUserIdAndProductId(userId, product.id());
        if (existing.isPresent()) {
            existing.get().updateEmail(email);
            return new AddResult(existing.get(), false);
        }
        if (repository.countByUserId(userId) >= maxItems) {
            throw new BadRequestException("Your wishlist can hold at most " + maxItems
                    + " products. Remove some to save new ones.");
        }
        WishlistItem item = repository.save(new WishlistItem(userId, email, product.id(), product.sku(), product.name(),
                product.firstImageUrl(), product.sellingPrice(), now));
        return new AddResult(item, true);
    }

    /** A number per customer for PostgreSQL's advisory lock. */
    static long lockKey(UUID userId) {
        return userId.getMostSignificantBits() ^ userId.getLeastSignificantBits();
    }
}

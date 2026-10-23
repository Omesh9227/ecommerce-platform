package com.ecommerce.wishlistservice.repository;

import com.ecommerce.wishlistservice.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, UUID> {

    List<WishlistItem> findByUserIdOrderByAddedAtDesc(UUID userId);

    Optional<WishlistItem> findByUserIdAndProductId(UUID userId, UUID productId);

    List<WishlistItem> findByUserIdAndProductIdIn(UUID userId, Collection<UUID> productIds);

    /** Everyone who saved this product (for price-drop emails). */
    List<WishlistItem> findByProductId(UUID productId);

    long countByUserId(UUID userId);

    @Transactional
    @Modifying
    @Query("DELETE FROM WishlistItem w WHERE w.userId = :userId AND w.productId = :productId")
    int deleteItem(@Param("userId") UUID userId, @Param("productId") UUID productId);

    @Transactional
    @Modifying
    @Query("DELETE FROM WishlistItem w WHERE w.userId = :userId")
    int deleteAllOfUser(@Param("userId") UUID userId);

    /**
     * Serialises changes to one customer's wishlist until the transaction ends (a PostgreSQL advisory lock, keyed by
     * the user id). Two simultaneous "add" clicks can then neither save the product twice nor go past the item limit.
     */
    @Query(value = "SELECT COUNT(*) FROM (SELECT pg_advisory_xact_lock(:key)) AS locked", nativeQuery = true)
    long lockWishlistOf(@Param("key") long key);
}

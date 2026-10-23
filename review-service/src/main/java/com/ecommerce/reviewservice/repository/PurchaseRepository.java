package com.ecommerce.reviewservice.repository;

import com.ecommerce.reviewservice.entity.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    /** Records "this customer received this product"; a repeated delivery event changes nothing. */
    @Modifying
    @Query(value = """
            INSERT INTO purchases (id, user_id, product_id, order_number, delivered_at)
            VALUES (gen_random_uuid(), :userId, :productId, :orderNumber, :deliveredAt)
            ON CONFLICT (user_id, product_id) DO NOTHING
            """, nativeQuery = true)
    int recordIfNew(@Param("userId") UUID userId, @Param("productId") UUID productId,
                    @Param("orderNumber") String orderNumber, @Param("deliveredAt") Instant deliveredAt);
}

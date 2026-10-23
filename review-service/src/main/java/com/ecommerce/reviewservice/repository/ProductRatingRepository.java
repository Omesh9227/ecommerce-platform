package com.ecommerce.reviewservice.repository;

import com.ecommerce.reviewservice.entity.ProductRating;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRatingRepository extends JpaRepository<ProductRating, UUID> {

    /** Creates the product's (empty) summary row if there is none yet - safe when two requests do it at once. */
    @Modifying
    @Query(value = """
            INSERT INTO product_ratings (product_id, review_count, average_rating, count_1, count_2, count_3, count_4,
                                         count_5, rating_version, updated_at)
            VALUES (:productId, 0, 0, 0, 0, 0, 0, 0, 0, :now)
            ON CONFLICT (product_id) DO NOTHING
            """, nativeQuery = true)
    int createIfMissing(@Param("productId") UUID productId, @Param("now") Instant now);

    /** The summary row, locked until the transaction ends: one recalculation per product at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductRating p WHERE p.productId = :productId")
    Optional<ProductRating> lockById(@Param("productId") UUID productId);
}

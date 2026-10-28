package com.ecommerce.reviewservice.repository;

import com.ecommerce.reviewservice.entity.Review;
import com.ecommerce.reviewservice.entity.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID>, JpaSpecificationExecutor<Review> {

    Optional<Review> findByProductIdAndUserId(UUID productId, UUID userId);

    Page<Review> findByUserId(UUID userId, Pageable pageable);

    /** [rating, count] rows for the product's reviews with this status. */
    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.productId = :productId AND r.status = :status GROUP BY r.rating")
    List<Object[]> countByRating(@Param("productId") UUID productId, @Param("status") ReviewStatus status);

    /** A delivery makes the customer's existing reviews of these products "verified purchase". */
    @Modifying
    @Query("""
            UPDATE Review r SET r.verifiedPurchase = true
             WHERE r.userId = :userId AND r.productId IN :productIds AND r.verifiedPurchase = false
            """)
    int markVerified(@Param("userId") UUID userId, @Param("productIds") Collection<UUID> productIds);

    // ---------- "Helpful" votes: atomic SQL, so simultaneous votes are all counted ----------

    /** @return 1 if this vote is new, 0 if the customer had already voted */
    @Modifying
    @Query(value = """
            INSERT INTO review_votes (review_id, user_id, created_at) VALUES (:reviewId, :userId, :now)
            ON CONFLICT (review_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int addVote(@Param("reviewId") UUID reviewId, @Param("userId") UUID userId, @Param("now") Instant now);

    /** @return 1 if a vote was removed, 0 if there was none */
    @Modifying
    @Query(value = "DELETE FROM review_votes WHERE review_id = :reviewId AND user_id = :userId", nativeQuery = true)
    int removeVote(@Param("reviewId") UUID reviewId, @Param("userId") UUID userId);

    @Modifying
    @Query(value = "UPDATE reviews SET helpful_count = helpful_count + :delta WHERE id = :reviewId", nativeQuery = true)
    int changeHelpfulCount(@Param("reviewId") UUID reviewId, @Param("delta") int delta);

    @Query(value = "SELECT helpful_count FROM reviews WHERE id = :reviewId", nativeQuery = true)
    int helpfulCount(@Param("reviewId") UUID reviewId);

    @Query(value = "SELECT COUNT(*) > 0 FROM review_votes WHERE review_id = :reviewId AND user_id = :userId", nativeQuery = true)
    boolean hasVoted(@Param("reviewId") UUID reviewId, @Param("userId") UUID userId);
}

package com.ecommerce.productservice.repository;

import com.ecommerce.productservice.entity.Product;
import com.ecommerce.productservice.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, UUID id);

    boolean existsByCategory_Id(UUID categoryId);

    /** Bulk lookup for other services (e.g. the cart): one query instead of one per product. */
    List<Product> findAllByIdInAndStatus(Collection<UUID> ids, ProductStatus status);

    /**
     * Applies stock from an INVENTORY_UPDATED event, only if it is newer than what we already have.
     * Native SQL on purpose: bypasses the product's @Version, so stock updates never clash with admin edits.
     *
     * @return 1 if applied, 0 if the product is unknown or the event is outdated
     */
    @Modifying
    @Query(value = """
            UPDATE products
               SET available_quantity = :availableQuantity, inventory_version = :inventoryVersion
             WHERE id = :productId AND inventory_version < :inventoryVersion
            """, nativeQuery = true)
    int updateAvailability(@Param("productId") UUID productId,
                           @Param("availableQuantity") int availableQuantity,
                           @Param("inventoryVersion") long inventoryVersion);

    /**
     * Applies a rating from a PRODUCT_RATING_UPDATED event, only if it is newer than what we already have.
     * Native SQL for the same reason as updateAvailability.
     *
     * @return 1 if applied, 0 if the product is unknown or the event is outdated
     */
    @Modifying
    @Query(value = """
            UPDATE products
               SET average_rating = :averageRating, review_count = :reviewCount, rating_version = :ratingVersion
             WHERE id = :productId AND rating_version < :ratingVersion
            """, nativeQuery = true)
    int updateRating(@Param("productId") UUID productId,
                     @Param("averageRating") BigDecimal averageRating,
                     @Param("reviewCount") int reviewCount,
                     @Param("ratingVersion") long ratingVersion);
}

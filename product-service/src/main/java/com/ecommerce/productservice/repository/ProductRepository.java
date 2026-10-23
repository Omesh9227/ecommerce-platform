package com.ecommerce.productservice.repository;

import com.ecommerce.productservice.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, UUID id);

    boolean existsByCategory_Id(UUID categoryId);

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
}

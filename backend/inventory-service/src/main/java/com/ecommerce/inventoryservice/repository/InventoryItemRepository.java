package com.ecommerce.inventoryservice.repository;

import com.ecommerce.inventoryservice.entity.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID>, JpaSpecificationExecutor<InventoryItem> {

    /**
     * Locks the row until the transaction ends (SELECT ... FOR UPDATE). Two admins or two orders changing
     * the same product's stock run one after the other, never on stale numbers.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryItem i WHERE i.productId = :productId")
    Optional<InventoryItem> findByIdForUpdate(@Param("productId") UUID productId);

    /** Locks several rows, always in productId order, so two multi-item reservations can't deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryItem i WHERE i.productId IN :productIds ORDER BY i.productId")
    List<InventoryItem> findAllByIdForUpdate(@Param("productIds") Collection<UUID> productIds);

    /**
     * Creates the item from a product event, or refreshes its product snapshot. Only a newer productVersion
     * updates an existing row, so duplicate or out-of-order events are harmless. Stock columns are never touched.
     *
     * @return 1 if a row was inserted or updated, 0 if the event was older than what we have
     */
    @Modifying
    @Query(value = """
            INSERT INTO inventory_items (product_id, sku, product_name, product_status, product_version,
                                         quantity_on_hand, quantity_reserved, low_stock_threshold, low_stock_alerted,
                                         created_at, updated_at, version)
            VALUES (:productId, :sku, :productName, :productStatus, :productVersion,
                    0, 0, :lowStockThreshold, FALSE, now(), now(), 0)
            ON CONFLICT (product_id) DO UPDATE
               SET sku = EXCLUDED.sku,
                   product_name = EXCLUDED.product_name,
                   product_status = EXCLUDED.product_status,
                   product_version = EXCLUDED.product_version,
                   updated_at = now()
             WHERE inventory_items.product_version < EXCLUDED.product_version
            """, nativeQuery = true)
    int upsertProductSnapshot(@Param("productId") UUID productId,
                              @Param("sku") String sku,
                              @Param("productName") String productName,
                              @Param("productStatus") String productStatus,
                              @Param("productVersion") long productVersion,
                              @Param("lowStockThreshold") int lowStockThreshold);
}

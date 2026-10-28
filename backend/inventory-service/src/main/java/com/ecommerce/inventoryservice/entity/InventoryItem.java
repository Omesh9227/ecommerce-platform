package com.ecommerce.inventoryservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Stock for one product.
 *
 * Rows are only ever created by the PRODUCT_CREATED consumer (native upsert), never by JPA, and the product
 * snapshot columns are only written by that upsert. They are mapped read-only (insertable/updatable = false)
 * so a stock update from JPA can never overwrite a newer snapshot with a stale one.
 */
@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@NoArgsConstructor
public class InventoryItem {

    public static final String PRODUCT_ACTIVE = "ACTIVE";

    @Id
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    // ----- Product snapshot (read-only for JPA) -----

    @Setter(AccessLevel.NONE)
    @Column(name = "sku", nullable = false, insertable = false, updatable = false)
    private String sku;

    @Setter(AccessLevel.NONE)
    @Column(name = "product_name", nullable = false, insertable = false, updatable = false)
    private String productName;

    @Setter(AccessLevel.NONE)
    @Column(name = "product_status", nullable = false, insertable = false, updatable = false)
    private String productStatus;

    @Setter(AccessLevel.NONE)
    @Column(name = "product_version", nullable = false, insertable = false, updatable = false)
    private long productVersion;

    // ----- Stock -----

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    @Column(name = "quantity_reserved", nullable = false)
    private int quantityReserved;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold;

    /** True once an INVENTORY_LOW alert was sent; reset when stock goes back above the threshold. */
    @Column(name = "low_stock_alerted", nullable = false)
    private boolean lowStockAlerted;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** For tests and mapping only: builds an item with a given snapshot (normally loaded from the database). */
    public static InventoryItem of(UUID productId, String sku, String productName, String productStatus,
                                   int onHand, int reserved, int threshold) {
        InventoryItem item = new InventoryItem();
        item.productId = productId;
        item.sku = sku;
        item.productName = productName;
        item.productStatus = productStatus;
        item.quantityOnHand = onHand;
        item.quantityReserved = reserved;
        item.lowStockThreshold = threshold;
        item.version = 0L;
        return item;
    }

    public int getAvailableQuantity() {
        return quantityOnHand - quantityReserved;
    }

    public boolean isLowStock() {
        return getAvailableQuantity() <= lowStockThreshold;
    }

    public boolean isSellable() {
        return PRODUCT_ACTIVE.equals(productStatus);
    }
}

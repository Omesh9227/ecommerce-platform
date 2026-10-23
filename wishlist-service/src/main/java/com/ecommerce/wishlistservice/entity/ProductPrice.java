package com.ecommerce.wishlistservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Latest price of a product, from product-events. Used to recognise old or repeated events. */
@Entity
@Table(name = "product_prices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductPrice {

    @Id
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "selling_price", precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "product_version", nullable = false)
    private long productVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProductPrice(UUID productId) {
        this.productId = productId;
        this.productVersion = -1;
    }

    /**
     * Takes the event's values if it's newer than what we have.
     *
     * @return false for an old or repeated event (nothing changed)
     */
    public boolean update(BigDecimal sellingPrice, String status, long productVersion, Instant now) {
        if (productVersion <= this.productVersion) {
            return false;
        }
        this.sellingPrice = sellingPrice;
        this.status = status;
        this.productVersion = productVersion;
        this.updatedAt = now;
        return true;
    }
}

package com.ecommerce.productservice.entity;

import com.ecommerce.productservice.util.PriceCalculator;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 5000)
    private String description;

    @Column(name = "brand", length = 100)
    private String brand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** List price (MRP). Set together with the discount through {@link #updatePricing}. */
    @Setter(AccessLevel.NONE)
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Setter(AccessLevel.NONE)
    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    /** Always derived from price and discount; never set directly. */
    @Setter(AccessLevel.NONE)
    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status;

    /**
     * Copy of inventory-service's available stock, written only by the INVENTORY_UPDATED consumer
     * (native UPDATE). Read-only for JPA, so saving a product can never overwrite a newer stock value.
     */
    @Setter(AccessLevel.NONE)
    @Column(name = "available_quantity", nullable = false, insertable = false, updatable = false)
    private int availableQuantity;

    @Setter(AccessLevel.NONE)
    @Column(name = "inventory_version", nullable = false, insertable = false, updatable = false)
    private long inventoryVersion;

    /**
     * Copy of review-service's rating summary, written only by the PRODUCT_RATING_UPDATED consumer (native UPDATE).
     * Read-only for JPA, like availableQuantity.
     */
    @Setter(AccessLevel.NONE)
    @Column(name = "average_rating", nullable = false, precision = 2, scale = 1, insertable = false, updatable = false)
    private BigDecimal averageRating;

    @Setter(AccessLevel.NONE)
    @Column(name = "review_count", nullable = false, insertable = false, updatable = false)
    private int reviewCount;

    @Setter(AccessLevel.NONE)
    @Column(name = "rating_version", nullable = false, insertable = false, updatable = false)
    private long ratingVersion;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "url", nullable = false, length = 500)
    private List<String> imageUrls = new ArrayList<>();

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Sets price and discount and recalculates the selling price, so the three can never disagree. */
    public void updatePricing(BigDecimal price, BigDecimal discountPercent) {
        BigDecimal discount = discountPercent == null ? BigDecimal.ZERO : discountPercent;
        this.price = PriceCalculator.normalize(price);
        this.discountPercent = PriceCalculator.normalize(discount);
        this.sellingPrice = PriceCalculator.sellingPrice(price, discount);
    }

    /** Replaces the image list in place (keeps Hibernate's collection wrapper). */
    public void replaceImageUrls(List<String> urls) {
        this.imageUrls.clear();
        if (urls != null) {
            this.imageUrls.addAll(urls);
        }
    }
}

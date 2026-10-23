package com.ecommerce.wishlistservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A product one customer saved. Name, image and price are a snapshot from when it was saved. */
@Entity
@Table(name = "wishlist_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "user_email")
    private String userEmail;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "price_when_added", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal priceWhenAdded;

    @Column(name = "last_alerted_price", precision = 12, scale = 2)
    private BigDecimal lastAlertedPrice;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    public WishlistItem(UUID userId, String userEmail, UUID productId, String sku, String productName, String imageUrl,
                        BigDecimal priceWhenAdded, Instant addedAt) {
        this.userId = userId;
        this.userEmail = userEmail;
        this.productId = productId;
        this.sku = sku;
        this.productName = productName;
        this.imageUrl = imageUrl;
        this.priceWhenAdded = priceWhenAdded;
        this.addedAt = addedAt;
    }

    /** The price the customer last saw from us: when they saved it, or our last price-drop email. */
    public BigDecimal priceLastSeen() {
        return lastAlertedPrice != null ? lastAlertedPrice : priceWhenAdded;
    }

    /** A price-drop email was sent at this price; the next one needs a lower price. */
    public void recordAlert(BigDecimal price) {
        this.lastAlertedPrice = price;
    }

    /** Saving an already saved product again keeps the item but updates the email (it may have changed). */
    public void updateEmail(String email) {
        if (email != null && !email.isBlank()) {
            this.userEmail = email;
        }
    }
}

package com.ecommerce.reviewservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One customer's review of one product (at most one each). */
@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Shown publicly, e.g. "Omesh K." (never the email address). */
    @Column(name = "reviewer_name", nullable = false, length = 100)
    private String reviewerName;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "comment", length = 4000)
    private String comment;

    @Column(name = "verified_purchase", nullable = false)
    private boolean verifiedPurchase;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ReviewStatus status;

    @Column(name = "hidden_reason", length = 300)
    private String hiddenReason;

    /** Changed only by SQL (atomic +1 / -1 per vote), never by saving the entity. */
    @Column(name = "helpful_count", nullable = false, insertable = false, updatable = false)
    private int helpfulCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** When the author last changed the text or rating (null = never). */
    @Column(name = "edited_at")
    private Instant editedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public Review(UUID productId, UUID userId, String reviewerName, int rating, String title, String comment,
                  boolean verifiedPurchase, Instant now) {
        this.productId = productId;
        this.userId = userId;
        this.reviewerName = reviewerName;
        this.rating = rating;
        this.title = title;
        this.comment = comment;
        this.verifiedPurchase = verifiedPurchase;
        this.status = ReviewStatus.VISIBLE;
        this.createdAt = now;
    }

    public void edit(int rating, String title, String comment, Instant now) {
        this.rating = rating;
        this.title = title;
        this.comment = comment;
        this.editedAt = now;
    }

    public void hide(String reason) {
        this.status = ReviewStatus.HIDDEN;
        this.hiddenReason = reason;
    }

    public void unhide() {
        this.status = ReviewStatus.VISIBLE;
        this.hiddenReason = null;
    }

    public boolean isVisible() {
        return status == ReviewStatus.VISIBLE;
    }
}

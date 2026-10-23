package com.ecommerce.reviewservice.service;

import com.ecommerce.reviewservice.client.CatalogProduct;
import com.ecommerce.reviewservice.client.ProductCatalogClient;
import com.ecommerce.reviewservice.client.UserProfileClient;
import com.ecommerce.reviewservice.config.ReviewProperties;
import com.ecommerce.reviewservice.dto.request.ReviewRequest;
import com.ecommerce.reviewservice.dto.response.ReviewResponse;
import com.ecommerce.reviewservice.exception.ConflictException;
import com.ecommerce.reviewservice.exception.ForbiddenException;
import com.ecommerce.reviewservice.exception.ResourceNotFoundException;
import com.ecommerce.reviewservice.repository.PurchaseRepository;
import com.ecommerce.reviewservice.repository.ReviewRepository;
import com.ecommerce.reviewservice.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Writing a review:
 * - the product must be for sale (product-service);
 * - one review per customer per product (edit it instead);
 * - "verified purchase" if the customer received the product (a delivered order, from order-events). When
 *   app.review.verified-purchase-required is on, only those customers may review at all;
 * - the name shown is "First L." from user-service (never the email).
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ProductCatalogClient catalogClient;
    private final UserProfileClient userProfileClient;
    private final PurchaseRepository purchaseRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewCommands commands;
    private final ReviewProperties properties;

    public ReviewResponse create(UUID productId, AuthenticatedUser user, String bearerToken, ReviewRequest request) {
        CatalogProduct product = catalogClient.findActiveProducts(List.of(productId)).get(productId);
        if (product == null) {
            throw new ResourceNotFoundException("Product not found or not available: " + productId);
        }
        boolean verified = purchaseRepository.existsByUserIdAndProductId(user.id(), productId);
        if (properties.verifiedPurchaseRequired() && !verified) {
            throw new ForbiddenException("Only customers who received this product can review it");
        }
        // Checked again inside the transaction; this just avoids calling user-service for nothing
        if (reviewRepository.findByProductIdAndUserId(productId, user.id()).isPresent()) {
            throw new ConflictException(ReviewCommands.ALREADY_REVIEWED);
        }
        String reviewerName = userProfileClient.displayName(bearerToken);
        try {
            return ReviewResponse.authorView(commands.create(productId, user, reviewerName, request, verified));
        } catch (DataIntegrityViolationException ex) {
            // Two "submit" clicks at once: the unique (product, user) constraint stopped the second
            throw new ConflictException(ReviewCommands.ALREADY_REVIEWED);
        }
    }

    public ReviewResponse edit(UUID reviewId, AuthenticatedUser user, ReviewRequest request) {
        return ReviewResponse.authorView(commands.edit(reviewId, user, request));
    }
}

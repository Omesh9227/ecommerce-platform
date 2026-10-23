package com.ecommerce.reviewservice.service;

import com.ecommerce.reviewservice.client.CatalogProduct;
import com.ecommerce.reviewservice.client.ProductCatalogClient;
import com.ecommerce.reviewservice.client.UserProfileClient;
import com.ecommerce.reviewservice.config.ReviewProperties;
import com.ecommerce.reviewservice.dto.request.ReviewRequest;
import com.ecommerce.reviewservice.entity.Review;
import com.ecommerce.reviewservice.exception.ConflictException;
import com.ecommerce.reviewservice.exception.ForbiddenException;
import com.ecommerce.reviewservice.repository.PurchaseRepository;
import com.ecommerce.reviewservice.repository.ReviewRepository;
import com.ecommerce.reviewservice.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReviewServiceTest {

    private final ProductCatalogClient catalog = mock(ProductCatalogClient.class);
    private final UserProfileClient users = mock(UserProfileClient.class);
    private final PurchaseRepository purchases = mock(PurchaseRepository.class);
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final ReviewCommands commands = mock(ReviewCommands.class);

    private final UUID productId = UUID.randomUUID();
    private final AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "omesh@example.com", "CUSTOMER");
    private final ReviewRequest request = new ReviewRequest(5, "Great", null);

    private ReviewService service(boolean buyersOnly) {
        CatalogProduct product = new CatalogProduct(productId, "PIXEL-8", "Pixel 8", BigDecimal.TEN, BigDecimal.TEN,
                "INR", "ACTIVE", 1, true, List.of());
        when(catalog.findActiveProducts(any())).thenReturn(Map.of(productId, product));
        when(reviews.findByProductIdAndUserId(productId, user.id())).thenReturn(Optional.empty());
        when(users.displayName(anyString())).thenReturn("Omesh K.");
        return new ReviewService(catalog, users, purchases, reviews, commands, new ReviewProperties(buyersOnly));
    }

    private Review saved(boolean verified) {
        return new Review(productId, user.id(), "Omesh K.", 5, "Great", null, verified, Instant.now());
    }

    @Test
    void buyersOnly_customerWhoNeverReceivedIt_isRefused_beforeAnythingIsCalled() {
        ReviewService service = service(true);
        when(purchases.existsByUserIdAndProductId(user.id(), productId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(productId, user, "Bearer x", request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only customers who received this product can review it");
        verifyNoInteractions(users, commands);
    }

    @Test
    void buyersOnly_buyerMayReview_asVerified() {
        ReviewService service = service(true);
        when(purchases.existsByUserIdAndProductId(user.id(), productId)).thenReturn(true);
        when(commands.create(eq(productId), eq(user), eq("Omesh K."), eq(request), eq(true))).thenReturn(saved(true));

        assertThat(service.create(productId, user, "Bearer x", request).verifiedPurchase()).isTrue();
    }

    @Test
    void openToAll_nonBuyerMayReview_unverified() {
        ReviewService service = service(false);
        when(purchases.existsByUserIdAndProductId(user.id(), productId)).thenReturn(false);
        when(commands.create(any(), any(), anyString(), any(), anyBoolean())).thenReturn(saved(false));

        assertThat(service.create(productId, user, "Bearer x", request).verifiedPurchase()).isFalse();
        verify(commands).create(productId, user, "Omesh K.", request, false);
    }

    @Test
    void alreadyReviewed_isRefused_withoutAskingUserService() {
        ReviewService service = service(false);
        when(reviews.findByProductIdAndUserId(productId, user.id())).thenReturn(Optional.of(saved(false)));

        assertThatThrownBy(() -> service.create(productId, user, "Bearer x", request)).isInstanceOf(ConflictException.class);
        verify(users, never()).displayName(anyString());
    }

    @Test
    void simultaneousSecondReview_hitsTheUniqueConstraint_andIsA409() {
        ReviewService service = service(false);
        when(commands.create(any(), any(), anyString(), any(), anyBoolean()))
                .thenThrow(new DataIntegrityViolationException("uk_reviews_product_user"));

        assertThatThrownBy(() -> service.create(productId, user, "Bearer x", request))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ReviewCommands.ALREADY_REVIEWED);
    }
}

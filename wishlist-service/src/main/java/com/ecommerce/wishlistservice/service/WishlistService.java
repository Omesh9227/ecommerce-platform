package com.ecommerce.wishlistservice.service;

import com.ecommerce.wishlistservice.client.CartClient;
import com.ecommerce.wishlistservice.client.CatalogProduct;
import com.ecommerce.wishlistservice.client.DownstreamUnavailableException;
import com.ecommerce.wishlistservice.client.ProductCatalogClient;
import com.ecommerce.wishlistservice.config.WishlistProperties;
import com.ecommerce.wishlistservice.dto.response.WishlistContainsResponse;
import com.ecommerce.wishlistservice.dto.response.WishlistItemResponse;
import com.ecommerce.wishlistservice.dto.response.WishlistItemStatus;
import com.ecommerce.wishlistservice.dto.response.WishlistResponse;
import com.ecommerce.wishlistservice.entity.WishlistItem;
import com.ecommerce.wishlistservice.exception.BadRequestException;
import com.ecommerce.wishlistservice.exception.ResourceNotFoundException;
import com.ecommerce.wishlistservice.repository.WishlistItemRepository;
import com.ecommerce.wishlistservice.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Wishlist rules:
 * - Only products that are for sale can be saved - but out-of-stock ones can (that's what wishlists are for).
 * - Saving a product twice keeps one entry (200 instead of 201). At most app.wishlist.max-items products.
 * - The stored item is a snapshot; price and stock are fetched live on every view, and price changes since saving
 *   are pointed out. If product-service is down the wishlist is still shown, from the snapshot.
 * - "Move to cart" adds it through cart-service (with the customer's own token, so all cart rules apply) and removes
 *   it from the wishlist only if that worked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WishlistService {

    static final int MAX_CONTAINS_IDS = 100;

    /** Result of an add: the item, and whether it was new (201) or already saved (200). */
    public record AddOutcome(WishlistItemResponse item, boolean created) {
    }

    private final WishlistItemRepository repository;
    private final WishlistStore store;
    private final ProductCatalogClient catalogClient;
    private final CartClient cartClient;
    private final WishlistProperties properties;
    private final Clock clock;

    // ---------- Read ----------

    public WishlistResponse getWishlist(UUID userId) {
        List<WishlistItem> items = repository.findByUserIdOrderByAddedAtDesc(userId);
        if (items.isEmpty()) {
            return new WishlistResponse(List.of(), 0, properties.maxItems(), properties.currency(), true, List.of());
        }

        Map<UUID, CatalogProduct> products;
        boolean upToDate;
        try {
            products = catalogClient.findActiveProducts(items.stream().map(WishlistItem::getProductId).toList());
            upToDate = true;
        } catch (DownstreamUnavailableException ex) {
            // Still show the wishlist (from the saved snapshot) rather than failing the whole page
            products = Map.of();
            upToDate = false;
        }

        List<WishlistItemResponse> responses = new ArrayList<>(items.size());
        for (WishlistItem item : items) {
            responses.add(toResponse(item, products.get(item.getProductId()), upToDate));
        }
        List<String> warnings = upToDate ? List.of()
                : List.of("Prices and stock could not be refreshed right now; showing them as they were when you saved each product.");
        return new WishlistResponse(responses, responses.size(), properties.maxItems(), properties.currency(), upToDate,
                warnings);
    }

    public WishlistContainsResponse contains(UUID userId, Collection<UUID> productIds) {
        Set<UUID> asked = new LinkedHashSet<>(productIds);
        if (asked.size() > MAX_CONTAINS_IDS) {
            throw new BadRequestException("Ask about at most " + MAX_CONTAINS_IDS + " products at a time");
        }
        if (asked.isEmpty()) {
            return new WishlistContainsResponse(List.of());
        }
        Set<UUID> saved = new LinkedHashSet<>();
        repository.findByUserIdAndProductIdIn(userId, asked).forEach(item -> saved.add(item.getProductId()));
        return new WishlistContainsResponse(asked.stream().filter(saved::contains).toList());
    }

    // ---------- Write ----------

    public AddOutcome add(AuthenticatedUser user, UUID productId) {
        CatalogProduct product = catalogClient.findActiveProducts(List.of(productId)).get(productId);
        if (product == null) {
            throw new ResourceNotFoundException("Product not found or not available for sale: " + productId);
        }
        WishlistStore.AddResult result = store.add(user.id(), user.email(), product, properties.maxItems(), clock.instant());
        if (result.created()) {
            log.info("Wishlist add userId={} productId={}", user.id(), productId);
        }
        return new AddOutcome(toResponse(result.item(), product, true), result.created());
    }

    public void remove(UUID userId, UUID productId) {
        if (repository.deleteItem(userId, productId) > 0) {
            log.info("Wishlist remove userId={} productId={}", userId, productId);
        }
    }

    public void clear(UUID userId) {
        int removed = repository.deleteAllOfUser(userId);
        log.info("Wishlist cleared userId={} ({} item(s))", userId, removed);
    }

    /**
     * Adds the product to the customer's cart, then removes it from the wishlist. If cart-service says no
     * (out of stock, cart full...) its answer is passed on and the product stays saved.
     */
    public WishlistResponse moveToCart(AuthenticatedUser user, String bearerToken, UUID productId, int quantity) {
        WishlistItem item = repository.findByUserIdAndProductId(user.id(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " is not in your wishlist"));

        cartClient.addItem(bearerToken, productId, quantity);

        repository.deleteItem(user.id(), productId);
        log.info("Wishlist move-to-cart userId={} productId={} quantity={} ({})", user.id(), productId, quantity,
                item.getProductName());
        return getWishlist(user.id());
    }

    // ---------- Building the response ----------

    private WishlistItemResponse toResponse(WishlistItem item, CatalogProduct product, boolean upToDate) {
        if (!upToDate || product == null) {
            WishlistItemStatus status = upToDate ? WishlistItemStatus.UNAVAILABLE : WishlistItemStatus.UNKNOWN;
            String message = upToDate ? "No longer available" : null;
            return new WishlistItemResponse(item.getProductId(), item.getSku(), item.getProductName(), item.getImageUrl(),
                    money(item.getPriceWhenAdded()), null, money(item.getPriceWhenAdded()), null, false, null, status,
                    message, item.getAddedAt());
        }

        BigDecimal price = money(product.sellingPrice());
        BigDecimal saved = money(item.getPriceWhenAdded());
        BigDecimal difference = price.subtract(saved);
        boolean dropped = difference.signum() < 0;

        List<String> messages = new ArrayList<>(2);
        WishlistItemStatus status;
        if (product.availableQuantity() <= 0) {
            status = WishlistItemStatus.OUT_OF_STOCK;
            messages.add("Out of stock");
        } else {
            status = WishlistItemStatus.AVAILABLE;
        }
        if (difference.signum() != 0) {
            messages.add((dropped ? "Price dropped" : "Price increased") + " from " + saved + " to " + price);
        }

        return new WishlistItemResponse(item.getProductId(), product.sku(), product.name(), product.firstImageUrl(),
                price, product.price() == null ? null : money(product.price()), saved, difference, dropped,
                product.availableQuantity(), status, messages.isEmpty() ? null : String.join(". ", messages),
                item.getAddedAt());
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }
}

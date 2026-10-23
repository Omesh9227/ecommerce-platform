package com.ecommerce.wishlistservice.service;

import com.ecommerce.wishlistservice.config.KafkaTopicsProperties;
import com.ecommerce.wishlistservice.config.WishlistProperties;
import com.ecommerce.wishlistservice.entity.ProductPrice;
import com.ecommerce.wishlistservice.entity.WishlistItem;
import com.ecommerce.wishlistservice.event.EventTypes;
import com.ecommerce.wishlistservice.event.ProductEvent;
import com.ecommerce.wishlistservice.event.WishlistEvent;
import com.ecommerce.wishlistservice.outbox.OutboxWriter;
import com.ecommerce.wishlistservice.repository.ProductPriceRepository;
import com.ecommerce.wishlistservice.repository.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Clock;

/**
 * Watches product prices (product-events) and tells customers when a product they saved gets cheaper:
 * <ul>
 *   <li>Only for products that are for sale (ACTIVE).</li>
 *   <li>Only when the new price is at least app.wishlist.price-drop-min-percent below the price the customer last saw
 *       - when they saved it, or in our previous email. So each email is about a new, lower price: no repeats when
 *       the price goes up and back down.</li>
 *   <li>Old or repeated events (Kafka delivers at least once) are recognised by productVersion and ignored.</li>
 * </ul>
 * The email itself is sent by notification-service: this writes WISHLIST_PRICE_DROPPED to the outbox, in the same
 * transaction that records the alert.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceDropService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ProductPriceRepository priceRepository;
    private final WishlistItemRepository itemRepository;
    private final OutboxWriter outboxWriter;
    private final KafkaTopicsProperties topics;
    private final WishlistProperties properties;
    private final Clock clock;

    /** @return how many price-drop alerts were written */
    @Transactional
    public int onProductEvent(ProductEvent event) {
        if (event.eventId() == null || event.eventType() == null || event.productId() == null) {
            throw new IllegalArgumentException("Product event " + event.eventId() + " is missing required fields");
        }
        Instant now = clock.instant();
        ProductPrice known = priceRepository.findById(event.productId()).orElseGet(() -> new ProductPrice(event.productId()));
        if (!known.update(event.sellingPrice(), event.status(), event.productVersion(), now)) {
            log.debug("Ignoring old/repeated product event {} v{}", event.productId(), event.productVersion());
            return 0;
        }
        priceRepository.save(known);

        BigDecimal newPrice = event.sellingPrice();
        if (!event.isActive() || newPrice == null) {
            return 0;
        }

        int alerts = 0;
        for (WishlistItem item : itemRepository.findByProductId(event.productId())) {
            if (!isWorthAnEmail(item.priceLastSeen(), newPrice)) {
                continue;
            }
            if (item.getUserEmail() == null || item.getUserEmail().isBlank()) {
                log.warn("Price drop for product {} but no email for user {}", event.productId(), item.getUserId());
                continue;
            }
            String name = event.name() != null ? event.name() : item.getProductName();
            outboxWriter.write(topics.wishlistEvents(), item.getUserId().toString(), EventTypes.WISHLIST_PRICE_DROPPED,
                    WishlistEvent.priceDropped(item, name, newPrice, properties.currency(), now));
            item.recordAlert(newPrice);
            alerts++;
        }
        if (alerts > 0) {
            log.info("Price drop on product {} to {}: {} customer(s) will be emailed", event.productId(), newPrice, alerts);
        }
        return alerts;
    }

    /** True if newPrice is at least the configured percentage below lastSeen. */
    boolean isWorthAnEmail(BigDecimal lastSeen, BigDecimal newPrice) {
        if (lastSeen == null || newPrice.compareTo(lastSeen) >= 0) {
            return false;
        }
        BigDecimal dropPercent = lastSeen.subtract(newPrice).multiply(HUNDRED).divide(lastSeen, 4, RoundingMode.HALF_UP);
        return dropPercent.compareTo(properties.priceDropMinPercent()) >= 0;
    }
}

package com.ecommerce.reviewservice.service;

import com.ecommerce.reviewservice.event.EventTypes;
import com.ecommerce.reviewservice.event.OrderEvent;
import com.ecommerce.reviewservice.repository.PurchaseRepository;
import com.ecommerce.reviewservice.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Learns from order-events who received what (ORDER_DELIVERED): that customer's reviews of those products - existing
 * and future ones - are "verified purchase". Other order events are ignored; a repeated event changes nothing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final ReviewRepository reviewRepository;
    private final Clock clock;

    /** @return how many existing reviews became verified */
    @Transactional
    public int onOrderEvent(OrderEvent event) {
        if (!EventTypes.ORDER_DELIVERED.equals(event.eventType())) {
            return 0;
        }
        if (event.eventId() == null || event.userId() == null || event.orderNumber() == null || event.items() == null) {
            throw new IllegalArgumentException("Order event " + event.eventId() + " is missing required fields");
        }
        Instant deliveredAt = event.occurredAt() != null ? event.occurredAt() : clock.instant();
        Set<UUID> productIds = new LinkedHashSet<>();
        event.items().stream().map(OrderEvent.Item::productId).filter(Objects::nonNull).forEach(productIds::add);
        if (productIds.isEmpty()) {
            return 0;
        }

        for (UUID productId : productIds) {
            purchaseRepository.recordIfNew(event.userId(), productId, event.orderNumber(), deliveredAt);
        }
        int verified = reviewRepository.markVerified(event.userId(), productIds);
        log.info("Order {} delivered: {} product(s) can now be reviewed as verified purchases ({} existing review(s) updated)",
                event.orderNumber(), productIds.size(), verified);
        return verified;
    }
}

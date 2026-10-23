package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.config.OrderProperties;
import com.ecommerce.orderservice.entity.OrderStatus;
import com.ecommerce.orderservice.repository.CustomerOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Housekeeping, every app.order.expiry-check-interval-ms:
 * - unpaid orders past their payment deadline are cancelled and their stock released;
 * - checkouts stuck in CREATED (service stopped mid-checkout) are failed and any reserved stock released.
 * Each order is handled in its own transaction; one failure (e.g. inventory-service down) doesn't stop the rest,
 * and that order is simply retried on the next run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpiryJob {

    private static final int BATCH_SIZE = 50;

    private final CustomerOrderRepository orderRepository;
    private final OrderLifecycleService lifecycleService;
    private final OrderProperties orderProperties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.order.expiry-check-interval-ms}",
            initialDelayString = "${app.order.expiry-check-interval-ms}")
    public void run() {
        expireUnpaidOrders();
        failStuckCheckouts();
    }

    /** @return how many orders were cancelled */
    public int expireUnpaidOrders() {
        List<UUID> due = orderRepository.findDueBefore(OrderStatus.PENDING_PAYMENT, clock.instant(), PageRequest.of(0, BATCH_SIZE));
        int done = 0;
        for (UUID id : due) {
            try {
                if (lifecycleService.expireUnpaid(id)) {
                    done++;
                }
            } catch (RuntimeException ex) {
                log.warn("Could not expire order {} (will retry): {}", id, ex.getMessage());
            }
        }
        if (done > 0) {
            log.info("Cancelled {} unpaid order(s) past their payment deadline", done);
        }
        return done;
    }

    /** @return how many checkouts were failed */
    public int failStuckCheckouts() {
        Instant before = clock.instant().minus(orderProperties.stuckCheckoutTimeout());
        List<UUID> stuck = orderRepository.findCreatedBefore(OrderStatus.CREATED, before, PageRequest.of(0, BATCH_SIZE));
        int done = 0;
        for (UUID id : stuck) {
            try {
                if (lifecycleService.failStuckCheckout(id)) {
                    done++;
                }
            } catch (RuntimeException ex) {
                log.warn("Could not clean up checkout {} (will retry): {}", id, ex.getMessage());
            }
        }
        return done;
    }
}

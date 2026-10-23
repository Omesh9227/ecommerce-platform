package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.config.InventoryProperties;
import com.ecommerce.inventoryservice.event.EventTypes;
import com.ecommerce.inventoryservice.event.ProductEvent;
import com.ecommerce.inventoryservice.repository.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Keeps inventory in step with the product catalog:
 * PRODUCT_CREATED creates the stock record (0 on hand), PRODUCT_UPDATED / PRODUCT_DISCONTINUED refresh its
 * SKU, name and status. Safe to receive the same event twice or out of order (productVersion check).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSnapshotService {

    private static final Set<String> HANDLED = Set.of(
            EventTypes.PRODUCT_CREATED, EventTypes.PRODUCT_UPDATED, EventTypes.PRODUCT_DISCONTINUED);

    private final InventoryItemRepository itemRepository;
    private final InventoryProperties inventoryProperties;

    @Transactional
    public void apply(ProductEvent event) {
        if (event.eventType() == null || !HANDLED.contains(event.eventType())) {
            log.debug("Ignoring product event type {}", event.eventType());
            return;
        }
        if (event.productId() == null || isBlank(event.sku()) || isBlank(event.name()) || isBlank(event.status())) {
            // Not retryable: goes to the dead-letter topic
            throw new IllegalArgumentException("Product event " + event.eventId() + " is missing required fields");
        }

        int rows = itemRepository.upsertProductSnapshot(event.productId(), event.sku(), event.name(),
                event.status(), event.productVersion(), inventoryProperties.defaultLowStockThreshold());

        if (rows == 0) {
            log.debug("Skipped outdated {} for product {} (version {})",
                    event.eventType(), event.productId(), event.productVersion());
        } else {
            log.info("Applied {} for product {} sku={} status={} version={}", event.eventType(),
                    event.productId(), event.sku(), event.status(), event.productVersion());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

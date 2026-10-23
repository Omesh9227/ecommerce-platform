package com.ecommerce.productservice.service;

import com.ecommerce.productservice.event.EventTypes;
import com.ecommerce.productservice.event.InventoryEvent;
import com.ecommerce.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps each product's availableQuantity in step with inventory-service, so product pages and searches
 * can show "in stock" without calling inventory-service on every request.
 * Older or duplicate events are ignored (inventoryVersion check in the UPDATE).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAvailabilityService {

    private final ProductRepository productRepository;

    @Transactional
    public void apply(InventoryEvent event) {
        if (!EventTypes.INVENTORY_UPDATED.equals(event.eventType())) {
            return; // e.g. INVENTORY_LOW is for notifications, not for us
        }
        if (event.productId() == null || event.availableQuantity() < 0) {
            throw new IllegalArgumentException("Inventory event " + event.eventId() + " has invalid fields");
        }

        int rows = productRepository.updateAvailability(event.productId(), event.availableQuantity(),
                event.inventoryVersion());
        if (rows == 1) {
            log.debug("Availability of product {} is now {} (inventory version {})",
                    event.productId(), event.availableQuantity(), event.inventoryVersion());
        } else {
            log.debug("Ignored inventory event for product {} (unknown product or outdated version {})",
                    event.productId(), event.inventoryVersion());
        }
    }
}

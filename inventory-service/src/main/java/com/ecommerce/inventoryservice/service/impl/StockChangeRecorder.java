package com.ecommerce.inventoryservice.service.impl;

import com.ecommerce.inventoryservice.entity.InventoryItem;
import com.ecommerce.inventoryservice.entity.MovementType;
import com.ecommerce.inventoryservice.entity.StockMovement;
import com.ecommerce.inventoryservice.outbox.InventoryEventPublisher;
import com.ecommerce.inventoryservice.repository.InventoryItemRepository;
import com.ecommerce.inventoryservice.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The one place every stock change ends up, so all of them behave the same:
 * 1. low-stock alert flags are updated (an alert fires once when stock drops to the threshold,
 *    and re-arms when stock goes back above it),
 * 2. items are saved and flushed (version incremented),
 * 3. movements are saved (audit trail),
 * 4. INVENTORY_UPDATED (and INVENTORY_LOW where needed) events go into the outbox - same transaction.
 */
@Component
@RequiredArgsConstructor
public class StockChangeRecorder {

    private final InventoryItemRepository itemRepository;
    private final StockMovementRepository movementRepository;
    private final InventoryEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(List<InventoryItem> items, List<StockMovement> movements) {
        List<InventoryItem> newlyLow = new ArrayList<>();
        for (InventoryItem item : items) {
            if (item.isLowStock() && !item.isLowStockAlerted()) {
                item.setLowStockAlerted(true);
                newlyLow.add(item);
            } else if (!item.isLowStock() && item.isLowStockAlerted()) {
                item.setLowStockAlerted(false);
            }
        }

        itemRepository.saveAllAndFlush(items);
        if (!movements.isEmpty()) {
            movementRepository.saveAll(movements);
        }

        items.forEach(eventPublisher::inventoryUpdated);
        newlyLow.forEach(eventPublisher::inventoryLow);
    }

    /** Builds a movement from the item's state AFTER the change has been applied to it. */
    public static StockMovement movement(InventoryItem item, MovementType type, int quantityChange,
                                         String reason, String reference, UUID actorId) {
        return StockMovement.builder()
                .productId(item.getProductId())
                .movementType(type)
                .quantityChange(quantityChange)
                .quantityOnHandAfter(item.getQuantityOnHand())
                .quantityReservedAfter(item.getQuantityReserved())
                .reason(reason == null || reason.isBlank() ? null : reason.trim())
                .reference(reference)
                .actorId(actorId)
                .build();
    }
}

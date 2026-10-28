package com.ecommerce.inventoryservice.service.impl;

import com.ecommerce.inventoryservice.entity.InventoryItem;
import com.ecommerce.inventoryservice.entity.MovementType;
import com.ecommerce.inventoryservice.entity.StockMovement;
import com.ecommerce.inventoryservice.outbox.InventoryEventPublisher;
import com.ecommerce.inventoryservice.repository.InventoryItemRepository;
import com.ecommerce.inventoryservice.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockChangeRecorderTest {

    @Mock
    private InventoryItemRepository itemRepository;
    @Mock
    private StockMovementRepository movementRepository;
    @Mock
    private InventoryEventPublisher eventPublisher;

    private StockChangeRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new StockChangeRecorder(itemRepository, movementRepository, eventPublisher);
    }

    private static InventoryItem item(int onHand, int reserved, int threshold) {
        return InventoryItem.of(UUID.randomUUID(), "SKU", "Name", "ACTIVE", onHand, reserved, threshold);
    }

    @Test
    void savesItemsAndMovements_thenWritesEvents() {
        InventoryItem item = item(20, 0, 5);
        List<StockMovement> movements = List.of(
                StockChangeRecorder.movement(item, MovementType.RESTOCK, 20, "  ", null, UUID.randomUUID()));

        recorder.record(List.of(item), movements);

        // The item must be flushed first, so the event carries the new version
        InOrder order = inOrder(itemRepository, movementRepository, eventPublisher);
        order.verify(itemRepository).saveAllAndFlush(List.of(item));
        order.verify(movementRepository).saveAll(movements);
        order.verify(eventPublisher).inventoryUpdated(item);
        verify(eventPublisher, never()).inventoryLow(any());
        assertThat(movements.get(0).getReason()).isNull(); // blank reason is stored as null
    }

    @Test
    void noMovements_skipsMovementSave() {
        InventoryItem item = item(20, 0, 5);

        recorder.record(List.of(item), List.of());

        verify(movementRepository, never()).saveAll(any());
        verify(eventPublisher).inventoryUpdated(item);
    }

    @Test
    void lowStockAlert_firesOnce_untilStockRecovers() {
        InventoryItem item = item(5, 0, 5); // available 5 <= threshold 5

        recorder.record(List.of(item), List.of());
        assertThat(item.isLowStockAlerted()).isTrue();

        item.setQuantityOnHand(3); // still low: no second alert
        recorder.record(List.of(item), List.of());
        verify(eventPublisher, times(1)).inventoryLow(item);

        item.setQuantityOnHand(50); // recovered: alert re-arms
        recorder.record(List.of(item), List.of());
        assertThat(item.isLowStockAlerted()).isFalse();

        item.setQuantityOnHand(2); // low again: alerts again
        recorder.record(List.of(item), List.of());
        verify(eventPublisher, times(2)).inventoryLow(item);
        verify(eventPublisher, times(4)).inventoryUpdated(item);
    }

    @Test
    void reservedStockCountsTowardsLowStock() {
        InventoryItem item = item(20, 16, 5); // 20 on hand but only 4 available

        recorder.record(List.of(item), List.of());

        verify(eventPublisher).inventoryLow(item);
    }
}

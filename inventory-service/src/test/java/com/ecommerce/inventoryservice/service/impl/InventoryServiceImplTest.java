package com.ecommerce.inventoryservice.service.impl;

import com.ecommerce.inventoryservice.dto.request.AdjustStockRequest;
import com.ecommerce.inventoryservice.dto.request.SetStockRequest;
import com.ecommerce.inventoryservice.dto.request.ThresholdRequest;
import com.ecommerce.inventoryservice.dto.response.InventoryItemResponse;
import com.ecommerce.inventoryservice.entity.InventoryItem;
import com.ecommerce.inventoryservice.entity.MovementType;
import com.ecommerce.inventoryservice.entity.StockMovement;
import com.ecommerce.inventoryservice.exception.BadRequestException;
import com.ecommerce.inventoryservice.exception.ResourceNotFoundException;
import com.ecommerce.inventoryservice.mapper.InventoryMapper;
import com.ecommerce.inventoryservice.repository.InventoryItemRepository;
import com.ecommerce.inventoryservice.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private InventoryItemRepository itemRepository;
    @Mock
    private StockMovementRepository movementRepository;
    @Mock
    private StockChangeRecorder stockChangeRecorder;
    @Captor
    private ArgumentCaptor<List<StockMovement>> movementsCaptor;

    private InventoryServiceImpl inventoryService;

    private final UUID adminId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryServiceImpl(itemRepository, movementRepository, stockChangeRecorder,
                new InventoryMapper());
    }

    private InventoryItem item(int onHand, int reserved) {
        return InventoryItem.of(productId, "PIXEL-8", "Pixel 8", "ACTIVE", onHand, reserved, 5);
    }

    private StockMovement recordedMovement(InventoryItem item) {
        verify(stockChangeRecorder).record(eq(List.of(item)), movementsCaptor.capture());
        assertThat(movementsCaptor.getValue()).hasSize(1);
        return movementsCaptor.getValue().get(0);
    }

    // ---------- setStock ----------

    @Test
    void setStock_setsExactQuantity_andRecordsAdjustment() {
        InventoryItem item = item(10, 2);
        when(itemRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(item));

        InventoryItemResponse response = inventoryService.setStock(productId, new SetStockRequest(25, " Stock count "), adminId);

        assertThat(response.quantityOnHand()).isEqualTo(25);
        assertThat(response.availableQuantity()).isEqualTo(23);
        StockMovement movement = recordedMovement(item);
        assertThat(movement.getMovementType()).isEqualTo(MovementType.ADJUSTMENT);
        assertThat(movement.getQuantityChange()).isEqualTo(15);
        assertThat(movement.getQuantityOnHandAfter()).isEqualTo(25);
        assertThat(movement.getQuantityReservedAfter()).isEqualTo(2);
        assertThat(movement.getReason()).isEqualTo("Stock count");
        assertThat(movement.getActorId()).isEqualTo(adminId);
    }

    @Test
    void setStock_belowReserved_isRejected() {
        InventoryItem item = item(10, 4);
        when(itemRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> inventoryService.setStock(productId, new SetStockRequest(3, null), adminId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("4 unit(s) are reserved");
        assertThat(item.getQuantityOnHand()).isEqualTo(10);
        verifyNoInteractions(stockChangeRecorder);
    }

    @Test
    void setStock_unknownProduct_isNotFound() {
        when(itemRepository.findByIdForUpdate(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.setStock(productId, new SetStockRequest(3, null), adminId))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(stockChangeRecorder);
    }

    // ---------- adjustStock ----------

    @Test
    void adjustStock_positive_isRestock() {
        InventoryItem item = item(10, 0);
        when(itemRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(item));

        InventoryItemResponse response = inventoryService.adjustStock(productId, new AdjustStockRequest(50, "Delivery"), adminId);

        assertThat(response.quantityOnHand()).isEqualTo(60);
        StockMovement movement = recordedMovement(item);
        assertThat(movement.getMovementType()).isEqualTo(MovementType.RESTOCK);
        assertThat(movement.getQuantityChange()).isEqualTo(50);
    }

    @Test
    void adjustStock_negative_isAdjustment() {
        InventoryItem item = item(10, 3);
        when(itemRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(item));

        InventoryItemResponse response = inventoryService.adjustStock(productId, new AdjustStockRequest(-7, "Damaged"), adminId);

        assertThat(response.quantityOnHand()).isEqualTo(3);
        assertThat(response.availableQuantity()).isZero();
        StockMovement movement = recordedMovement(item);
        assertThat(movement.getMovementType()).isEqualTo(MovementType.ADJUSTMENT);
        assertThat(movement.getQuantityChange()).isEqualTo(-7);
    }

    @Test
    void adjustStock_zero_isRejectedWithoutTouchingTheDatabase() {
        assertThatThrownBy(() -> inventoryService.adjustStock(productId, new AdjustStockRequest(0, null), adminId))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(itemRepository, stockChangeRecorder);
    }

    @Test
    void adjustStock_removingReservedUnits_isRejected() {
        InventoryItem item = item(10, 3);
        when(itemRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> inventoryService.adjustStock(productId, new AdjustStockRequest(-8, null), adminId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("only 7 available");
        assertThat(item.getQuantityOnHand()).isEqualTo(10);
        verifyNoInteractions(stockChangeRecorder);
    }

    // ---------- threshold ----------

    @Test
    void updateThreshold_recordsChangeWithoutMovement() {
        InventoryItem item = item(8, 0);
        when(itemRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(item));

        InventoryItemResponse response = inventoryService.updateThreshold(productId, new ThresholdRequest(10));

        assertThat(response.lowStockThreshold()).isEqualTo(10);
        assertThat(response.lowStock()).isTrue();
        verify(stockChangeRecorder).record(List.of(item), List.of());
    }

    // ---------- reads ----------

    @Test
    void list_rejectsUnknownSortField() {
        assertThatThrownBy(() -> inventoryService.list(null, null, PageRequest.of(0, 20, Sort.by("password"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot sort by 'password'");
        verifyNoInteractions(itemRepository);
    }

    @Test
    void movements_unknownProduct_isNotFound() {
        when(itemRepository.existsById(productId)).thenReturn(false);

        assertThatThrownBy(() -> inventoryService.movements(productId, PageRequest.of(0, 20, Sort.by("createdAt"))))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(movementRepository);
    }
}

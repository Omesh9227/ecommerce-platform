package com.ecommerce.inventoryservice.service.impl;

import com.ecommerce.inventoryservice.dto.request.AdjustStockRequest;
import com.ecommerce.inventoryservice.dto.request.SetStockRequest;
import com.ecommerce.inventoryservice.dto.request.ThresholdRequest;
import com.ecommerce.inventoryservice.dto.response.InventoryItemResponse;
import com.ecommerce.inventoryservice.dto.response.PageResponse;
import com.ecommerce.inventoryservice.dto.response.StockMovementResponse;
import com.ecommerce.inventoryservice.entity.InventoryItem;
import com.ecommerce.inventoryservice.entity.MovementType;
import com.ecommerce.inventoryservice.exception.BadRequestException;
import com.ecommerce.inventoryservice.exception.ResourceNotFoundException;
import com.ecommerce.inventoryservice.mapper.InventoryMapper;
import com.ecommerce.inventoryservice.repository.InventoryItemRepository;
import com.ecommerce.inventoryservice.repository.InventoryItemSpecifications;
import com.ecommerce.inventoryservice.repository.StockMovementRepository;
import com.ecommerce.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    static final Set<String> SORTABLE_ITEM_FIELDS =
            Set.of("sku", "productName", "quantityOnHand", "quantityReserved", "lowStockThreshold", "updatedAt");
    static final Set<String> SORTABLE_MOVEMENT_FIELDS = Set.of("createdAt");

    private final InventoryItemRepository itemRepository;
    private final StockMovementRepository movementRepository;
    private final StockChangeRecorder stockChangeRecorder;
    private final InventoryMapper mapper;

    // ---------- Read ----------

    @Override
    @Transactional(readOnly = true)
    public InventoryItemResponse getItem(UUID productId) {
        return mapper.toResponse(itemRepository.findById(productId)
                .orElseThrow(() -> notFound(productId)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryItemResponse> list(Boolean lowStock, String search, Pageable pageable) {
        validateSort(pageable.getSort(), SORTABLE_ITEM_FIELDS);
        return PageResponse.from(itemRepository
                .findAll(InventoryItemSpecifications.withFilters(lowStock, search), pageable)
                .map(mapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> movements(UUID productId, Pageable pageable) {
        validateSort(pageable.getSort(), SORTABLE_MOVEMENT_FIELDS);
        if (!itemRepository.existsById(productId)) {
            throw notFound(productId);
        }
        return PageResponse.from(movementRepository.findByProductId(productId, pageable).map(mapper::toResponse));
    }

    // ---------- Write ----------

    @Override
    @Transactional
    public InventoryItemResponse setStock(UUID productId, SetStockRequest request, UUID actorId) {
        InventoryItem item = lockItem(productId);
        int newOnHand = request.quantityOnHand();
        if (newOnHand < item.getQuantityReserved()) {
            throw new BadRequestException("Cannot set on-hand stock to " + newOnHand + ": "
                    + item.getQuantityReserved() + " unit(s) are reserved for orders");
        }

        int change = newOnHand - item.getQuantityOnHand();
        item.setQuantityOnHand(newOnHand);
        stockChangeRecorder.record(List.of(item), List.of(
                StockChangeRecorder.movement(item, MovementType.ADJUSTMENT, change, request.reason(), null, actorId)));

        log.info("Stock set productId={} onHand={} (change {}) by={}", productId, newOnHand, change, actorId);
        return mapper.toResponse(item);
    }

    @Override
    @Transactional
    public InventoryItemResponse adjustStock(UUID productId, AdjustStockRequest request, UUID actorId) {
        int change = request.quantityChange();
        if (change == 0) {
            throw new BadRequestException("quantityChange cannot be 0");
        }

        InventoryItem item = lockItem(productId);
        int newOnHand = item.getQuantityOnHand() + change;
        if (newOnHand < item.getQuantityReserved()) {
            throw new BadRequestException("Cannot remove " + (-change) + " unit(s): only "
                    + item.getAvailableQuantity() + " available (" + item.getQuantityReserved() + " reserved for orders)");
        }

        item.setQuantityOnHand(newOnHand);
        MovementType type = change > 0 ? MovementType.RESTOCK : MovementType.ADJUSTMENT;
        stockChangeRecorder.record(List.of(item), List.of(
                StockChangeRecorder.movement(item, type, change, request.reason(), null, actorId)));

        log.info("Stock adjusted productId={} change={} onHand={} by={}", productId, change, newOnHand, actorId);
        return mapper.toResponse(item);
    }

    @Override
    @Transactional
    public InventoryItemResponse updateThreshold(UUID productId, ThresholdRequest request) {
        InventoryItem item = lockItem(productId);
        item.setLowStockThreshold(request.lowStockThreshold());
        stockChangeRecorder.record(List.of(item), List.of());
        return mapper.toResponse(item);
    }

    // ---------- Helpers ----------

    private InventoryItem lockItem(UUID productId) {
        return itemRepository.findByIdForUpdate(productId).orElseThrow(() -> notFound(productId));
    }

    private static ResourceNotFoundException notFound(UUID productId) {
        return new ResourceNotFoundException("Inventory item for product", productId);
    }

    static void validateSort(Sort sort, Set<String> allowed) {
        for (Sort.Order order : sort) {
            if (!allowed.contains(order.getProperty())) {
                throw new BadRequestException("Cannot sort by '" + order.getProperty() + "'. Allowed: "
                        + String.join(", ", allowed.stream().sorted().toList()));
            }
        }
    }
}

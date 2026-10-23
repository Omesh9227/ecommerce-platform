package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.dto.request.AdjustStockRequest;
import com.ecommerce.inventoryservice.dto.request.SetStockRequest;
import com.ecommerce.inventoryservice.dto.request.ThresholdRequest;
import com.ecommerce.inventoryservice.dto.response.InventoryItemResponse;
import com.ecommerce.inventoryservice.dto.response.PageResponse;
import com.ecommerce.inventoryservice.dto.response.StockMovementResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface InventoryService {

    InventoryItemResponse getItem(UUID productId);

    PageResponse<InventoryItemResponse> list(Boolean lowStock, String search, Pageable pageable);

    InventoryItemResponse setStock(UUID productId, SetStockRequest request, UUID actorId);

    InventoryItemResponse adjustStock(UUID productId, AdjustStockRequest request, UUID actorId);

    InventoryItemResponse updateThreshold(UUID productId, ThresholdRequest request);

    PageResponse<StockMovementResponse> movements(UUID productId, Pageable pageable);
}

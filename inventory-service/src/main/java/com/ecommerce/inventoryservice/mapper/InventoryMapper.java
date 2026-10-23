package com.ecommerce.inventoryservice.mapper;

import com.ecommerce.inventoryservice.dto.response.InventoryItemResponse;
import com.ecommerce.inventoryservice.dto.response.ReservationResponse;
import com.ecommerce.inventoryservice.dto.response.StockMovementResponse;
import com.ecommerce.inventoryservice.entity.InventoryItem;
import com.ecommerce.inventoryservice.entity.Reservation;
import com.ecommerce.inventoryservice.entity.StockMovement;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
public class InventoryMapper {

    public InventoryItemResponse toResponse(InventoryItem item) {
        return new InventoryItemResponse(
                item.getProductId(),
                item.getSku(),
                item.getProductName(),
                item.getProductStatus(),
                item.getQuantityOnHand(),
                item.getQuantityReserved(),
                item.getAvailableQuantity(),
                item.getLowStockThreshold(),
                item.isLowStock(),
                item.getUpdatedAt()
        );
    }

    public StockMovementResponse toResponse(StockMovement movement) {
        return new StockMovementResponse(
                movement.getId(),
                movement.getMovementType(),
                movement.getQuantityChange(),
                movement.getQuantityOnHandAfter(),
                movement.getQuantityReservedAfter(),
                movement.getReason(),
                movement.getReference(),
                movement.getActorId(),
                movement.getCreatedAt()
        );
    }

    public ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getOrderReference(),
                reservation.getStatus(),
                reservation.getItems().stream()
                        .sorted(Comparator.comparing(i -> i.getProductId().toString()))
                        .map(i -> new ReservationResponse.Item(i.getProductId(), i.getQuantity()))
                        .toList(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt()
        );
    }
}

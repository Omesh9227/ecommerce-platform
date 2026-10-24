package com.ecommerce.orderservice.mapper;

import com.ecommerce.orderservice.dto.response.OrderItemResponse;
import com.ecommerce.orderservice.dto.response.OrderResponse;
import com.ecommerce.orderservice.dto.response.OrderSummaryResponse;
import com.ecommerce.orderservice.dto.response.ShippingAddressResponse;
import com.ecommerce.orderservice.dto.response.TimelineEntryResponse;
import com.ecommerce.orderservice.entity.CustomerOrder;
import com.ecommerce.orderservice.entity.OrderStatusHistory;
import com.ecommerce.orderservice.entity.ShippingAddress;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMapper {

    public OrderResponse toResponse(CustomerOrder order, List<OrderStatusHistory> history) {
        ShippingAddress a = order.getShippingAddress();
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getUserId(),
                order.getStatus(),
                order.getStatusReason(),
                order.getItems().stream()
                        .map(i -> new OrderItemResponse(i.getProductId(), i.getSku(), i.getProductName(), i.getImageUrl(),
                                i.getUnitPrice(), i.getListPrice(), i.getQuantity(), i.getLineTotal()))
                        .toList(),
                order.getItemCount(),
                order.getSubtotal(),
                order.getShippingFee(),
                order.getTotalAmount(),
                order.getCurrency(),
                a == null ? null : new ShippingAddressResponse(a.getFullName(), a.getPhone(), a.getLine1(), a.getLine2(),
                        a.getCity(), a.getState(), a.getPostalCode(), a.getCountry()),
                order.getPaymentDueAt(),
                order.getCarrier(),
                order.getTrackingNumber(),
                order.getEstimatedDeliveryDate(),
                order.getPlacedAt(),
                order.getConfirmedAt(),
                order.getShippedAt(),
                order.getDeliveredAt(),
                order.getCancelledAt(),
                order.getCreatedAt(),
                history.stream()
                        .map(h -> new TimelineEntryResponse(h.getFromStatus(), h.getToStatus(), h.getChangedBy(),
                                h.getNote(), h.getChangedAt()))
                        .toList());
    }

    public OrderSummaryResponse toSummary(CustomerOrder order) {
        return new OrderSummaryResponse(order.getOrderNumber(), order.getUserId(), order.getStatus(),
                order.getItemCount(), order.getTotalAmount(), order.getCurrency(), order.getCreatedAt(), order.getPlacedAt());
    }
}

package com.ecommerce.shippingservice.dto.response;

import com.ecommerce.shippingservice.entity.DeliveryAddress;
import com.ecommerce.shippingservice.entity.Shipment;
import com.ecommerce.shippingservice.entity.ShipmentItem;
import com.ecommerce.shippingservice.entity.ShipmentStatus;
import com.ecommerce.shippingservice.entity.TrackingEvent;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A shipment as its customer (or an admin) sees it, with the full address and tracking history. userId: admins only. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShipmentResponse(
        UUID id,
        @Schema(example = "ORD-3FA85F645717") String orderNumber,
        ShipmentStatus status,
        @Schema(description = "Why it was cancelled, or why the last delivery attempt failed") String statusReason,
        @Schema(example = "Delhivery") String carrier,
        @Schema(example = "EC482019375610") String trackingNumber,
        @Schema(example = "2026-10-27") LocalDate estimatedDeliveryDate,
        int failedAttempts,
        Address deliveryAddress,
        List<Item> items,
        List<TrackingEventResponse> tracking,
        Instant createdAt,
        Instant packedAt,
        Instant shippedAt,
        Instant deliveredAt,
        Instant cancelledAt,
        UUID userId
) {

    public record Address(String recipientName, String phone, String line1, String line2, String city, String state,
                          String postalCode, String country) {

        static Address of(DeliveryAddress a) {
            return new Address(a.getRecipientName(), a.getPhone(), a.getLine1(), a.getLine2(), a.getCity(), a.getState(),
                    a.getPostalCode(), a.getCountry());
        }
    }

    public record Item(UUID productId, String sku, String productName, int quantity) {

        static Item of(ShipmentItem i) {
            return new Item(i.getProductId(), i.getSku(), i.getProductName(), i.getQuantity());
        }
    }

    public static ShipmentResponse of(Shipment s, List<TrackingEvent> tracking, boolean forAdmin) {
        return new ShipmentResponse(s.getId(), s.getOrderNumber(), s.getStatus(), s.getStatusReason(), s.getCarrier(),
                s.getTrackingNumber(), s.getEstimatedDeliveryDate(), s.getFailedAttempts(), Address.of(s.getAddress()),
                s.getItems().stream().map(Item::of).toList(), tracking.stream().map(TrackingEventResponse::of).toList(),
                s.getCreatedAt(), s.getPackedAt(), s.getShippedAt(), s.getDeliveredAt(), s.getCancelledAt(),
                forAdmin ? s.getUserId() : null);
    }
}

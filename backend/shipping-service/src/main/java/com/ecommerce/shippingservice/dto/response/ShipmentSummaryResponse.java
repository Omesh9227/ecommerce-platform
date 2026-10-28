package com.ecommerce.shippingservice.dto.response;

import com.ecommerce.shippingservice.entity.Shipment;
import com.ecommerce.shippingservice.entity.ShipmentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A row in a list of shipments. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShipmentSummaryResponse(
        UUID id,
        String orderNumber,
        ShipmentStatus status,
        String carrier,
        String trackingNumber,
        LocalDate estimatedDeliveryDate,
        String city,
        String postalCode,
        int itemCount,
        Instant createdAt,
        Instant updatedAt
) {

    public static ShipmentSummaryResponse of(Shipment s) {
        return new ShipmentSummaryResponse(s.getId(), s.getOrderNumber(), s.getStatus(), s.getCarrier(),
                s.getTrackingNumber(), s.getEstimatedDeliveryDate(), s.getAddress().getCity(),
                s.getAddress().getPostalCode(), s.getItems().stream().mapToInt(i -> i.getQuantity()).sum(),
                s.getCreatedAt(), s.getUpdatedAt());
    }
}

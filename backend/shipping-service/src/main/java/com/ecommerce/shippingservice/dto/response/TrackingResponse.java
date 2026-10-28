package com.ecommerce.shippingservice.dto.response;

import com.ecommerce.shippingservice.entity.Shipment;
import com.ecommerce.shippingservice.entity.ShipmentStatus;
import com.ecommerce.shippingservice.entity.TrackingEvent;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The public tracking page for a tracking number: where the parcel is and when it should arrive. No order number,
 * names, phone or street address - only the destination city.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrackingResponse(
        @Schema(example = "EC482019375610") String trackingNumber,
        @Schema(example = "Delhivery") String carrier,
        ShipmentStatus status,
        @Schema(example = "2026-10-27") LocalDate estimatedDeliveryDate,
        @Schema(example = "Bengaluru, Karnataka") String destination,
        Instant shippedAt,
        Instant deliveredAt,
        List<TrackingEventResponse> events
) {

    public static TrackingResponse of(Shipment s, List<TrackingEvent> events) {
        return new TrackingResponse(s.getTrackingNumber(), s.getCarrier(), s.getStatus(),
                s.getStatus() == ShipmentStatus.DELIVERED ? null : s.getEstimatedDeliveryDate(),
                s.getAddress().getCity() + ", " + s.getAddress().getState(), s.getShippedAt(), s.getDeliveredAt(),
                events.stream().map(TrackingEventResponse::of).toList());
    }
}

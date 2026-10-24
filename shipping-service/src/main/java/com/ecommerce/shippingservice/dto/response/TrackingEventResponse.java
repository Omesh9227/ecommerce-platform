package com.ecommerce.shippingservice.dto.response;

import com.ecommerce.shippingservice.entity.ShipmentStatus;
import com.ecommerce.shippingservice.entity.TrackingEvent;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** One line of the tracking history. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrackingEventResponse(
        ShipmentStatus status,
        @Schema(example = "Hyderabad hub") String location,
        @Schema(example = "In transit") String description,
        Instant occurredAt
) {

    public static TrackingEventResponse of(TrackingEvent e) {
        return new TrackingEventResponse(e.getStatus(), e.getLocation(), e.getDescription(), e.getOccurredAt());
    }
}

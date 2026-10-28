package com.ecommerce.orderservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Optional reason for cancelling")
public record CancelOrderRequest(
        @Schema(example = "Ordered by mistake")
        @Size(max = 200, message = "reason must be at most 200 characters")
        String reason
) {
}

package com.ecommerce.adminservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * How many orders are in a status right now.
 *
 * @param oldestSince when the oldest of them entered the status (for statuses that are waiting for something)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PipelineStage(String status, long orders, Instant oldestSince) {
}

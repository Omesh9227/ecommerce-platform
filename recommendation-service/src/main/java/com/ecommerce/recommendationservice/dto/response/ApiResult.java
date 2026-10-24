package com.ecommerce.recommendationservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard success envelope: { "success": true, "message": "...", "data": {...} }.
 * Named ApiResult (not ApiResponse) to avoid clashing with Swagger's @ApiResponse annotation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResult<T>(boolean success, String message, T data) {

    public static <T> ApiResult<T> of(String message, T data) {
        return new ApiResult<>(true, message, data);
    }
}

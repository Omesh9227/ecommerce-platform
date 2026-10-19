package com.ecommerce.userservice.dto.response;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserSummaryResponse user
) {
}

package com.ecommerce.orderservice.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID productId,
        String sku,
        String productName,
        String imageUrl,
        BigDecimal unitPrice,
        BigDecimal listPrice,
        int quantity,
        BigDecimal lineTotal
) {
}

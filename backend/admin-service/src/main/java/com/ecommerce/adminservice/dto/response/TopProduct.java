package com.ecommerce.adminservice.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/** A best seller of the period (paid orders that weren't cancelled, at the prices they were sold for). */
public record TopProduct(UUID productId, String sku, String name, long unitsSold, BigDecimal revenue, long orders) {
}

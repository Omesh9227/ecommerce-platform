package com.ecommerce.productservice.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money maths in one place. BigDecimal only (never double) and always 2 decimal places, HALF_UP.
 * Example: price 999.00, discount 15% → selling price 849.15.
 */
public final class PriceCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 2;

    private PriceCalculator() {
    }

    public static BigDecimal sellingPrice(BigDecimal price, BigDecimal discountPercent) {
        if (price == null) {
            throw new IllegalArgumentException("price is required");
        }
        BigDecimal discount = discountPercent == null ? BigDecimal.ZERO : discountPercent;
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("price must be greater than 0");
        }
        if (discount.signum() < 0 || discount.compareTo(HUNDRED) >= 0) {
            throw new IllegalArgumentException("discount must be between 0 and 100 (exclusive)");
        }
        BigDecimal multiplier = HUNDRED.subtract(discount).divide(HUNDRED, 10, RoundingMode.HALF_UP);
        return price.multiply(multiplier).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal normalize(BigDecimal amount) {
        return amount == null ? null : amount.setScale(SCALE, RoundingMode.HALF_UP);
    }
}

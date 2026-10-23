package com.ecommerce.cartservice.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * The cart with live prices.
 *
 * @param totalQuantity   all units in the cart
 * @param subtotal        total of the lines that can be bought now (status AVAILABLE)
 * @param totalSavings    discount on those lines (list price minus selling price)
 * @param checkoutReady   true if the cart is not empty and every line is AVAILABLE
 * @param pricesUpToDate  false if product-service could not be reached (prices and stock may be outdated)
 * @param warnings        cart-level messages for the shopper
 */
public record CartResponse(
        List<CartItemResponse> items,
        int totalQuantity,
        BigDecimal subtotal,
        BigDecimal totalSavings,
        String currency,
        boolean checkoutReady,
        boolean pricesUpToDate,
        List<String> warnings
) {

    public static CartResponse empty(String currency) {
        return new CartResponse(List.of(), 0, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), currency,
                false, true, List.of());
    }
}

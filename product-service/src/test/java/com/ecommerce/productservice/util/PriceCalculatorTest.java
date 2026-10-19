package com.ecommerce.productservice.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceCalculatorTest {

    @ParameterizedTest(name = "{0} at {1}% -> {2}")
    @CsvSource({
            "999.00,   15,    849.15",
            "79900,    10,    71910.00",
            "33.33,    33.33, 22.22",
            "100,      0,     100.00",
            "0.01,     50,    0.01",
            "1999.99,  99.99, 0.20"
    })
    void sellingPrice_isRoundedHalfUpToTwoDecimals(String price, String discount, String expected) {
        BigDecimal result = PriceCalculator.sellingPrice(new BigDecimal(price), new BigDecimal(discount));

        assertThat(result).isEqualByComparingTo(expected);
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    void nullDiscount_meansNoDiscount() {
        assertThat(PriceCalculator.sellingPrice(new BigDecimal("500"), null)).isEqualByComparingTo("500.00");
    }

    @Test
    void hundredPercentDiscount_isRejected() {
        assertThatThrownBy(() -> PriceCalculator.sellingPrice(new BigDecimal("10"), new BigDecimal("100")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeDiscount_isRejected() {
        assertThatThrownBy(() -> PriceCalculator.sellingPrice(new BigDecimal("10"), new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroPrice_isRejected() {
        assertThatThrownBy(() -> PriceCalculator.sellingPrice(BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

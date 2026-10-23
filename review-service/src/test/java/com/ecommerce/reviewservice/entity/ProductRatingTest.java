package com.ecommerce.reviewservice.entity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRatingTest {

    @ParameterizedTest(name = "{0}x1 {1}x2 {2}x3 {3}x4 {4}x5 stars -> {5}")
    @CsvSource({
            "0, 0, 0, 0, 0, 0.0",
            "0, 0, 0, 0, 1, 5.0",
            "0, 0, 0, 3, 1, 4.3",   // 4.25 rounds up
            "0, 0, 0, 2, 1, 4.3",   // 4.33
            "1, 0, 0, 0, 1, 3.0",
            "1, 1, 0, 0, 0, 1.5",
            "0, 0, 2, 1, 0, 3.3"    // 3.33
    })
    void average_hasOneDecimal_roundedHalfUp(int c1, int c2, int c3, int c4, int c5, String expected) {
        assertThat(ProductRating.average(c1, c2, c3, c4, c5)).isEqualByComparingTo(expected);
        assertThat(ProductRating.average(c1, c2, c3, c4, c5).scale()).isEqualTo(1);
    }
}

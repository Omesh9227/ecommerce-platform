package com.ecommerce.productservice.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSpecificationsTest {

    @Test
    void escapeLike_treatsWildcardsAsLiteralText() {
        assertThat(ProductSpecifications.escapeLike("50%")).isEqualTo("50\\%");
        assertThat(ProductSpecifications.escapeLike("usb_c")).isEqualTo("usb\\_c");
        assertThat(ProductSpecifications.escapeLike("a\\b")).isEqualTo("a\\\\b");
        assertThat(ProductSpecifications.escapeLike("iphone")).isEqualTo("iphone");
    }
}

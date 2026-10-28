package com.ecommerce.reviewservice.client;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileClientTest {

    @ParameterizedTest(name = "\"{0}\" \"{1}\" -> {2}")
    @CsvSource(value = {
            "omesh | kumar | Omesh K.",
            "Omesh | ' ' | Omesh",
            "'  priya ' | sharma | Priya S.",
            "' ' | kumar | Customer",
            "NULL | NULL | Customer"
    }, delimiter = '|', nullValues = "NULL")
    void reviewerName_isFirstNameAndInitial_neverMoreThanThat(String first, String last, String expected) {
        assertThat(UserProfileClient.displayName(first, last)).isEqualTo(expected);
    }
}

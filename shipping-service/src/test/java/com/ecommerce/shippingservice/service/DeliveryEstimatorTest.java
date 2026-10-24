package com.ecommerce.shippingservice.service;

import com.ecommerce.shippingservice.config.ShippingProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryEstimatorTest {

    private static final ShippingProperties PROPERTIES = new ShippingProperties("560001", "Asia/Kolkata",
            new ShippingProperties.DeliveryDays(1, 2, 3, 5), List.of("Delhivery", "Blue Dart"));

    private final DeliveryEstimator estimator = new DeliveryEstimator(PROPERTIES,
            Clock.fixed(Instant.parse("2026-10-24T06:00:00Z"), ZoneOffset.UTC));

    @ParameterizedTest(name = "{0}: {1} day(s)")
    @CsvSource({
            "560034, 1",   // Bengaluru: same city
            "562110, 2",   // Bengaluru rural: same region (56x)
            "500081, 3",   // Hyderabad: same zone (5xxxxx)
            "600001, 5",   // Chennai: other zone
            "110001, 5"    // Delhi
    })
    void daysByDistance(String postalCode, int days) {
        assertThat(estimator.deliveryDays(postalCode)).isEqualTo(days);
    }

    @Test
    void countsFromTheWarehouseDayNotUtc() {
        // 20:00 UTC on 24 Oct is already 01:30 on 25 Oct in India
        assertThat(estimator.estimate("560034", Instant.parse("2026-10-24T20:00:00Z"))).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(estimator.estimate("560034", Instant.parse("2026-10-24T10:00:00Z"))).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(estimator.estimateFromNow("110001")).isEqualTo(LocalDate.of(2026, 10, 29));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "56001", "0560001", "060001", "56A001"})
    void rejectsInvalidPinCodes(String postalCode) {
        assertThatThrownBy(() -> estimator.deliveryDays(postalCode)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void carriersMatchIgnoringCase() {
        assertThat(PROPERTIES.carrier(" blue dart ")).contains("Blue Dart");
        assertThat(PROPERTIES.carrier("FedEx")).isEmpty();
        assertThat(PROPERTIES.carrier(null)).isEmpty();
    }

    @Test
    void invalidTimeZoneIsRejectedAtStartup() {
        assertThatThrownBy(() -> new ShippingProperties("560001", "India/Bengaluru",
                new ShippingProperties.DeliveryDays(1, 2, 3, 5), List.of("Delhivery")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("India/Bengaluru");
    }

    @Test
    void trackingNumbersAreNormalized() {
        assertThat(TrackingNumbers.normalize(" dlv-123456 ")).isEqualTo("DLV-123456");
        assertThat(TrackingNumbers.normalize("  ")).isNull();
    }
}

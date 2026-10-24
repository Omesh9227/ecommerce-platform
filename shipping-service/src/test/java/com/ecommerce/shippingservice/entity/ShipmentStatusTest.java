package com.ecommerce.shippingservice.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShipmentStatusTest {

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({
            "PENDING, PACKED, true",
            "PENDING, CANCELLED, true",
            "PENDING, SHIPPED, false",
            "PACKED, SHIPPED, true",
            "PACKED, CANCELLED, true",
            "PACKED, DELIVERED, false",
            "SHIPPED, IN_TRANSIT, true",
            "SHIPPED, OUT_FOR_DELIVERY, true",
            "SHIPPED, DELIVERED, true",
            "SHIPPED, CANCELLED, false",
            "SHIPPED, DELIVERY_FAILED, false",
            "IN_TRANSIT, IN_TRANSIT, true",
            "IN_TRANSIT, OUT_FOR_DELIVERY, true",
            "IN_TRANSIT, CANCELLED, false",
            "OUT_FOR_DELIVERY, DELIVERED, true",
            "OUT_FOR_DELIVERY, DELIVERY_FAILED, true",
            "OUT_FOR_DELIVERY, IN_TRANSIT, false",
            "DELIVERY_FAILED, OUT_FOR_DELIVERY, true",
            "DELIVERY_FAILED, IN_TRANSIT, true",
            "DELIVERY_FAILED, DELIVERED, false",
            "DELIVERED, IN_TRANSIT, false",
            "DELIVERED, CANCELLED, false",
            "CANCELLED, PACKED, false"
    })
    void allowedMoves(ShipmentStatus from, ShipmentStatus to, boolean allowed) {
        assertThat(from.canMoveTo(to)).isEqualTo(allowed);
    }

    @Test
    void warehouseAndShippedGroups() {
        assertThat(ShipmentStatus.PENDING.isInWarehouse()).isTrue();
        assertThat(ShipmentStatus.PACKED.isInWarehouse()).isTrue();
        assertThat(ShipmentStatus.SHIPPED.hasShipped()).isTrue();
        assertThat(ShipmentStatus.DELIVERED.hasShipped()).isTrue();
        assertThat(ShipmentStatus.CANCELLED.hasShipped()).isFalse();
        assertThat(ShipmentStatus.PENDING.isPacked()).isFalse();
        assertThat(ShipmentStatus.CANCELLED.isPacked()).isFalse();
        assertThat(ShipmentStatus.DELIVERY_FAILED.isPacked()).isTrue();
    }

    @Test
    void movesSetTimestampsAndCountFailedAttempts() {
        Instant t = Instant.parse("2026-10-24T05:00:00Z");
        Shipment s = Shipment.create(UUID.randomUUID(), "ORD-1", UUID.randomUUID(), "a@b.com",
                new DeliveryAddress("A", "1", "L1", null, "Pune", "Maharashtra", "411001", "IN"),
                LocalDate.of(2026, 10, 27), 3, t);
        s.moveTo(ShipmentStatus.PACKED, null, t.plusSeconds(60));
        s.ship("Delhivery", "EC1", LocalDate.of(2026, 10, 28), t.plusSeconds(120));
        s.moveTo(ShipmentStatus.OUT_FOR_DELIVERY, null, t.plusSeconds(180));
        s.moveTo(ShipmentStatus.DELIVERY_FAILED, "Customer not available", t.plusSeconds(240));
        assertThat(s.getStatusReason()).isEqualTo("Customer not available");
        s.moveTo(ShipmentStatus.OUT_FOR_DELIVERY, null, t.plusSeconds(300));
        s.moveTo(ShipmentStatus.DELIVERED, null, t.plusSeconds(360));

        assertThat(s.getPackedAt()).isEqualTo(t.plusSeconds(60));
        assertThat(s.getShippedAt()).isEqualTo(t.plusSeconds(120));
        assertThat(s.getDeliveredAt()).isEqualTo(t.plusSeconds(360));
        assertThat(s.getFailedAttempts()).isEqualTo(1);
        assertThat(s.getStatusReason()).isNull();
        assertThat(s.getEstimatedDeliveryDate()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThatThrownBy(() -> s.moveTo(ShipmentStatus.IN_TRANSIT, null, t)).isInstanceOf(IllegalStateException.class);
    }
}

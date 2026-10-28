package com.ecommerce.orderservice.entity;

import com.ecommerce.orderservice.config.OrderProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderModelTest {

    private static final ShippingAddress ADDRESS =
            new ShippingAddress("Omesh", "9876543210", "Line 1", null, "Pune", "MH", "411001", "IN");

    private static OrderItem item(String price, int quantity) {
        return new OrderItem(UUID.randomUUID(), "SKU", "Name", null, new BigDecimal(price), null, quantity);
    }

    @Test
    void lifecycle_allowsOnlyForwardSteps() {
        assertThat(OrderStatus.CREATED.canMoveTo(OrderStatus.PENDING_PAYMENT)).isTrue();
        assertThat(OrderStatus.CREATED.canMoveTo(OrderStatus.CHECKOUT_FAILED)).isTrue();
        assertThat(OrderStatus.PENDING_PAYMENT.canMoveTo(OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStatus.PENDING_PAYMENT.canMoveTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canMoveTo(OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStatus.SHIPPED.canMoveTo(OrderStatus.DELIVERED)).isTrue();

        assertThat(OrderStatus.CONFIRMED.canMoveTo(OrderStatus.CANCELLED)).isTrue(); // paid orders: refunded
        assertThat(OrderStatus.CONFIRMED.canMoveTo(OrderStatus.DELIVERED)).isFalse();
        assertThat(OrderStatus.SHIPPED.canMoveTo(OrderStatus.CANCELLED)).isFalse();
        for (OrderStatus next : OrderStatus.values()) {
            assertThat(OrderStatus.DELIVERED.canMoveTo(next)).isFalse();
            assertThat(OrderStatus.CANCELLED.canMoveTo(next)).isFalse();
            assertThat(OrderStatus.CHECKOUT_FAILED.canMoveTo(next)).isFalse();
        }
        assertThat(OrderStatus.CREATED.isVisibleToCustomer()).isFalse();
        assertThat(OrderStatus.CHECKOUT_FAILED.isVisibleToCustomer()).isFalse();
        assertThat(OrderStatus.CANCELLED.isVisibleToCustomer()).isTrue();
    }

    @Test
    void orderId_isTheSameForTheSameUserAndKey_only() {
        UUID user = UUID.randomUUID();
        assertThat(CustomerOrder.idFor(user, "key-12345")).isEqualTo(CustomerOrder.idFor(user, "key-12345"));
        assertThat(CustomerOrder.idFor(user, "key-12345")).isNotEqualTo(CustomerOrder.idFor(user, "key-67890"));
        assertThat(CustomerOrder.idFor(user, "key-12345")).isNotEqualTo(CustomerOrder.idFor(UUID.randomUUID(), "key-12345"));
        assertThat(CustomerOrder.orderNumberFor(UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6")))
                .isEqualTo("ORD-3FA85F645717");
    }

    @Test
    void create_computesTotals() {
        CustomerOrder order = CustomerOrder.create(UUID.randomUUID(), "a@b.com", "key-12345", "INR",
                List.of(item("99.99", 3), item("10.00", 1)), new BigDecimal("40.00"), ADDRESS);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getItemCount()).isEqualTo(4);
        assertThat(order.getSubtotal()).isEqualByComparingTo("309.97");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("349.97");
        assertThat(order.getItems()).allSatisfy(i -> assertThat(i.getOrder()).isSameAs(order));
        assertThat(order.getOrderNumber()).isEqualTo(CustomerOrder.orderNumberFor(order.getId()));
    }

    @Test
    void moveTo_recordsTimestamps_andRefusesInvalidSteps() {
        CustomerOrder order = CustomerOrder.create(UUID.randomUUID(), "a@b.com", "key-12345", "INR",
                List.of(item("10.00", 1)), BigDecimal.ZERO, ADDRESS);
        Instant now = Instant.parse("2026-10-23T10:00:00Z");

        assertThatThrownBy(() -> order.moveTo(OrderStatus.SHIPPED, now, null)).isInstanceOf(IllegalStateException.class);
        order.moveTo(OrderStatus.PENDING_PAYMENT, now, null);
        assertThat(order.getPlacedAt()).isEqualTo(now);
        order.moveTo(OrderStatus.CANCELLED, now, "Changed my mind");
        assertThat(order.getCancelledAt()).isEqualTo(now);
        assertThat(order.getStatusReason()).isEqualTo("Changed my mind");
    }

    @Test
    void shippingIsFreeFromTheThreshold() {
        OrderProperties properties = new OrderProperties("INR", new BigDecimal("40"), new BigDecimal("499"),
                Duration.ofMinutes(30), Duration.ofMinutes(2), 30000);

        assertThat(properties.shippingFeeFor(new BigDecimal("498.99"))).isEqualByComparingTo("40.00");
        assertThat(properties.shippingFeeFor(new BigDecimal("499.00"))).isEqualByComparingTo("0.00");
    }
}

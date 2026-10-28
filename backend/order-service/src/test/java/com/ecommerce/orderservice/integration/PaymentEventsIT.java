package com.ecommerce.orderservice.integration;

import com.ecommerce.orderservice.event.EventTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** payment-events arrive over Kafka and drive the order (as published by payment-service). */
class PaymentEventsIT extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private void send(String type, String orderNumber) throws Exception {
        UUID orderId = orderId(orderNumber);
        String json = """
                {"eventId":"%s","eventType":"%s","occurredAt":"2026-10-23T10:00:00Z","paymentId":"%s","orderId":"%s",
                 "orderNumber":"%s","userId":"%s","status":"SUCCEEDED","amount":900.00,"currency":"INR","method":"UPI",
                 "cardBrand":null,"cardLast4":null,"reason":null,"paymentVersion":1}
                """.formatted(UUID.randomUUID(), type, UUID.randomUUID(), orderId, orderNumber, customerId);
        kafkaTemplate.send("payment-events", orderId.toString(), json).get(10, TimeUnit.SECONDS);
    }

    @Test
    void paymentSucceededOnKafka_confirmsTheOrder() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        String orderNumber = placeOrder(customerAuth, phone, 1);

        send(EventTypes.PAYMENT_SUCCEEDED, orderNumber);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(orderStatus(orderNumber)).isEqualTo("CONFIRMED"));
        assertThat(stub.reservation(orderNumber).status).isEqualTo("CONFIRMED");
    }

    @Test
    void paymentFailed_leavesTheOrderWaitingForPayment() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        String failed = placeOrder(customerAuth, phone, 1);
        String paid = placeOrder(customerAuth, phone, 1);

        send(EventTypes.PAYMENT_FAILED, failed);
        send(EventTypes.PAYMENT_SUCCEEDED, paid); // same partition, after the failure: once it's applied, so was the failure

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(orderStatus(paid)).isEqualTo("CONFIRMED"));
        assertThat(orderStatus(failed)).isEqualTo("PENDING_PAYMENT");
    }
}

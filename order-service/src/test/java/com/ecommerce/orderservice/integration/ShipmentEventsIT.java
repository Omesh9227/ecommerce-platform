package com.ecommerce.orderservice.integration;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** shipment-events (shipping-service) move paid orders to SHIPPED and DELIVERED. */
class ShipmentEventsIT extends AbstractIntegrationTest {

    private void publish(String key, Object event) throws Exception {
        kafka().send("shipment-events", key, objectMapper.writeValueAsString(event)).get();
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

    private org.springframework.kafka.core.KafkaTemplate<String, String> kafka() {
        return kafkaTemplate;
    }

    @Test
    void eventsOnKafkaShipAndDeliverTheOrder() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        String orderNumber = placeOrder(customerAuth, phone, 1);
        pay(orderNumber);
        String key = orderId(orderNumber).toString();

        publish(key, shipmentEvent("SHIPMENT_SHIPPED", orderNumber, 3, null));
        publish(key, shipmentEvent("SHIPMENT_DELIVERED", orderNumber, 5, "Received by security"));
        await().atMost(Duration.ofSeconds(20)).until(() -> "DELIVERED".equals(orderStatus(orderNumber)));

        List<ConsumerRecord<String, String>> events = awaitEvents(key, 4);
        assertThat(events.stream().map(r -> {
            try {
                return eventType(r.value());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).toList())
                .containsExactly("ORDER_PLACED", "ORDER_CONFIRMED", "ORDER_SHIPPED", "ORDER_DELIVERED");
    }

    @Test
    void deliveredWithoutShippedStillCompletesTheOrder() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        String orderNumber = placeOrder(customerAuth, phone, 1);
        pay(orderNumber);

        shipment("SHIPMENT_DELIVERED", orderNumber, 9); // the SHIPPED event was dead-lettered

        mockMvc.perform(get(ORDERS_URL + "/" + orderNumber).header(HttpHeaders.AUTHORIZATION, customerAuth))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.carrier").value("Delhivery"))
                .andExpect(jsonPath("$.data.timeline[*].toStatus").value(org.hamcrest.Matchers.contains(
                        "PENDING_PAYMENT", "CONFIRMED", "SHIPPED", "DELIVERED")));
        shipment("SHIPMENT_SHIPPED", orderNumber, 4); // arrives late: ignored
        assertThat(orderStatus(orderNumber)).isEqualTo("DELIVERED");
    }

    @Test
    void shippedJustAfterTheCustomerCancelled_staysCancelledAndNotesTheRecall() throws Exception {
        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        String orderNumber = placeOrder(customerAuth, phone, 1);
        pay(orderNumber);
        mockMvc.perform(post(ORDERS_URL + "/" + orderNumber + "/cancel").header(HttpHeaders.AUTHORIZATION, customerAuth))
                .andExpect(status().isOk());

        shipment("SHIPMENT_SHIPPED", orderNumber, 3);
        shipment("SHIPMENT_DELIVERED", orderNumber, 4);

        mockMvc.perform(get(ORDERS_URL + "/" + orderNumber).header(HttpHeaders.AUTHORIZATION, customerAuth))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.timeline[3].note")
                        .value("The parcel left the warehouse after the order was cancelled - it is being recalled"));
        assertThat(count("SELECT COUNT(*) FROM order_status_history h JOIN orders o ON o.id = h.order_id "
                + "WHERE o.order_number = ? AND h.note LIKE 'The parcel left%'", orderNumber)).isEqualTo(1);
    }

    @Test
    void unknownOrdersAndMalformedEventsDoNotBlockTheTopic() throws Exception {
        String badKey = UUID.randomUUID().toString();
        kafka().send("shipment-events", badKey, "{oops").get();
        UUID unknown = UUID.randomUUID();
        publish(unknown.toString(), new com.ecommerce.orderservice.event.ShipmentEvent(UUID.randomUUID(), "SHIPMENT_SHIPPED",
                unknown, "ORD-UNKNOWN", "Delhivery", "EC1", null, null, null, 2));

        awaitRecords("shipment-events.DLT", badKey, 1);

        UUID phone = stub.product("PHONE", "1000.00", "900.00", 10);
        String orderNumber = placeOrder(customerAuth, phone, 1);
        pay(orderNumber);
        publish(orderId(orderNumber).toString(), shipmentEvent("SHIPMENT_SHIPPED", orderNumber, 3, null));
        await().atMost(Duration.ofSeconds(20)).until(() -> "SHIPPED".equals(orderStatus(orderNumber)));
    }

    private String eventType(String json) throws Exception {
        return objectMapper.readTree(json).get("eventType").asText();
    }

    private int count(String sql, Object... args) {
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }
}

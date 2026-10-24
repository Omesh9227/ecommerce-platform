package com.ecommerce.adminservice.messaging;

import com.ecommerce.adminservice.event.InventoryEvent;
import com.ecommerce.adminservice.event.OrderEvent;
import com.ecommerce.adminservice.event.PaymentEvent;
import com.ecommerce.adminservice.event.ShipmentEvent;
import com.ecommerce.adminservice.ingest.EventRecorder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** The four topics the dashboard is built from. Failures are retried, then dead-lettered (see KafkaConfig). */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventListeners {

    private final ObjectMapper objectMapper;
    private final EventRecorder recorder;

    @KafkaListener(topics = "${app.kafka.topics.order-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received order event key={} offset={}", record.key(), record.offset());
        recorder.apply(objectMapper.readValue(record.value(), OrderEvent.class));
    }

    @KafkaListener(topics = "${app.kafka.topics.payment-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received payment event key={} offset={}", record.key(), record.offset());
        recorder.apply(objectMapper.readValue(record.value(), PaymentEvent.class));
    }

    @KafkaListener(topics = "${app.kafka.topics.inventory-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onInventoryEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received inventory event key={} offset={}", record.key(), record.offset());
        recorder.apply(objectMapper.readValue(record.value(), InventoryEvent.class));
    }

    @KafkaListener(topics = "${app.kafka.topics.shipment-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onShipmentEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received shipment event key={} offset={}", record.key(), record.offset());
        recorder.apply(objectMapper.readValue(record.value(), ShipmentEvent.class));
    }
}

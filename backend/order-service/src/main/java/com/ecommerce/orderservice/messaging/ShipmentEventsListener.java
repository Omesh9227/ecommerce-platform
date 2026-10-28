package com.ecommerce.orderservice.messaging;

import com.ecommerce.orderservice.event.ShipmentEvent;
import com.ecommerce.orderservice.service.OrderLifecycleService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes shipment-events (shipped, out for delivery, delivery failed, delivered). Failures are retried, then dead-lettered. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentEventsListener {

    private final ObjectMapper objectMapper;
    private final OrderLifecycleService lifecycleService;

    @KafkaListener(topics = "${app.kafka.topics.shipment-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onShipmentEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received shipment event key={} partition={} offset={}", record.key(), record.partition(), record.offset());
        lifecycleService.applyShipmentEvent(objectMapper.readValue(record.value(), ShipmentEvent.class));
    }
}

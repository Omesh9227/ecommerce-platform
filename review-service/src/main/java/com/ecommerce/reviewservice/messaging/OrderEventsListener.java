package com.ecommerce.reviewservice.messaging;

import com.ecommerce.reviewservice.event.OrderEvent;
import com.ecommerce.reviewservice.service.PurchaseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes order-events (deliveries). Failures are retried, then dead-lettered (see KafkaConfig). */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventsListener {

    private final ObjectMapper objectMapper;
    private final PurchaseService purchaseService;

    @KafkaListener(topics = "${app.kafka.topics.order-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received order event key={} partition={} offset={}", record.key(), record.partition(), record.offset());
        purchaseService.onOrderEvent(objectMapper.readValue(record.value(), OrderEvent.class));
    }
}

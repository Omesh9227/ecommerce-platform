package com.ecommerce.paymentservice.messaging;

import com.ecommerce.paymentservice.event.OrderEvent;
import com.ecommerce.paymentservice.service.OrderEventHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes order-events. Failures are retried, then dead-lettered (see KafkaConfig). */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventsListener {

    private final ObjectMapper objectMapper;
    private final OrderEventHandler handler;

    @KafkaListener(topics = "${app.kafka.topics.order-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received order event key={} partition={} offset={}", record.key(), record.partition(), record.offset());
        handler.handle(objectMapper.readValue(record.value(), OrderEvent.class));
    }
}

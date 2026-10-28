package com.ecommerce.orderservice.messaging;

import com.ecommerce.orderservice.event.PaymentEvent;
import com.ecommerce.orderservice.service.PaymentEventHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes payment-events. Failures are retried, then dead-lettered (see KafkaConfig). */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventsListener {

    private final ObjectMapper objectMapper;
    private final PaymentEventHandler handler;

    @KafkaListener(topics = "${app.kafka.topics.payment-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received payment event key={} partition={} offset={}", record.key(), record.partition(), record.offset());
        handler.handle(objectMapper.readValue(record.value(), PaymentEvent.class));
    }
}

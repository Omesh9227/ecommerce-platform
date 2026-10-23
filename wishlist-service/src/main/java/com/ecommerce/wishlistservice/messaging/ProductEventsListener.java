package com.ecommerce.wishlistservice.messaging;

import com.ecommerce.wishlistservice.event.ProductEvent;
import com.ecommerce.wishlistservice.service.PriceDropService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes product-events (price changes). Failures are retried, then dead-lettered (see KafkaConfig). */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventsListener {

    private final ObjectMapper objectMapper;
    private final PriceDropService priceDropService;

    @KafkaListener(topics = "${app.kafka.topics.product-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onProductEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received product event key={} partition={} offset={}", record.key(), record.partition(), record.offset());
        priceDropService.onProductEvent(objectMapper.readValue(record.value(), ProductEvent.class));
    }
}

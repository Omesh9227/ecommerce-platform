package com.ecommerce.inventoryservice.messaging;

import com.ecommerce.inventoryservice.event.ProductEvent;
import com.ecommerce.inventoryservice.service.ProductSnapshotService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes product-events. Errors are handled by the DefaultErrorHandler in KafkaConfig
 * (retry, then dead-letter topic), so this method just lets exceptions propagate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventsListener {

    private final ObjectMapper objectMapper;
    private final ProductSnapshotService productSnapshotService;

    @KafkaListener(topics = "${app.kafka.topics.product-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onProductEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received product event key={} partition={} offset={}", record.key(), record.partition(), record.offset());
        ProductEvent event = objectMapper.readValue(record.value(), ProductEvent.class);
        productSnapshotService.apply(event);
    }
}

package com.ecommerce.searchservice.messaging;

import com.ecommerce.searchservice.event.CategoryEvent;
import com.ecommerce.searchservice.event.InventoryEvent;
import com.ecommerce.searchservice.event.ProductEvent;
import com.ecommerce.searchservice.event.ReviewEvent;
import com.ecommerce.searchservice.index.CatalogIndexer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** The four topics the search index is built from. Failures are retried, then dead-lettered (see KafkaConfig). */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventListeners {

    private final ObjectMapper objectMapper;
    private final CatalogIndexer indexer;

    @KafkaListener(topics = "${app.kafka.topics.product-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onProductEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received product event key={} offset={}", record.key(), record.offset());
        indexer.apply(objectMapper.readValue(record.value(), ProductEvent.class));
    }

    @KafkaListener(topics = "${app.kafka.topics.category-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onCategoryEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received category event key={} offset={}", record.key(), record.offset());
        indexer.apply(objectMapper.readValue(record.value(), CategoryEvent.class));
    }

    @KafkaListener(topics = "${app.kafka.topics.inventory-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onInventoryEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received inventory event key={} offset={}", record.key(), record.offset());
        indexer.apply(objectMapper.readValue(record.value(), InventoryEvent.class));
    }

    @KafkaListener(topics = "${app.kafka.topics.review-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onReviewEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received review event key={} offset={}", record.key(), record.offset());
        indexer.apply(objectMapper.readValue(record.value(), ReviewEvent.class));
    }
}

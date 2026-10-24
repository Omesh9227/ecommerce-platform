package com.ecommerce.recommendationservice.messaging;

import com.ecommerce.recommendationservice.catalog.CatalogIndexer;
import com.ecommerce.recommendationservice.event.CategoryEvent;
import com.ecommerce.recommendationservice.event.InventoryEvent;
import com.ecommerce.recommendationservice.event.OrderEvent;
import com.ecommerce.recommendationservice.event.ProductEvent;
import com.ecommerce.recommendationservice.event.ReviewEvent;
import com.ecommerce.recommendationservice.orders.PurchaseHistory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** The five topics recommendations are built from. Failures are retried, then dead-lettered (see KafkaConfig). */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventListeners {

    private final ObjectMapper objectMapper;
    private final CatalogIndexer catalog;
    private final PurchaseHistory purchases;

    @KafkaListener(topics = "${app.kafka.topics.product-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onProductEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received product event key={} offset={}", record.key(), record.offset());
        catalog.apply(objectMapper.readValue(record.value(), ProductEvent.class));
    }

    @KafkaListener(topics = "${app.kafka.topics.category-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onCategoryEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received category event key={} offset={}", record.key(), record.offset());
        catalog.apply(objectMapper.readValue(record.value(), CategoryEvent.class));
    }

    @KafkaListener(topics = "${app.kafka.topics.inventory-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onInventoryEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received inventory event key={} offset={}", record.key(), record.offset());
        catalog.apply(objectMapper.readValue(record.value(), InventoryEvent.class));
    }

    @KafkaListener(topics = "${app.kafka.topics.review-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onReviewEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received review event key={} offset={}", record.key(), record.offset());
        catalog.apply(objectMapper.readValue(record.value(), ReviewEvent.class));
    }

    @KafkaListener(topics = "${app.kafka.topics.order-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received order event key={} offset={}", record.key(), record.offset());
        purchases.apply(objectMapper.readValue(record.value(), OrderEvent.class));
    }
}

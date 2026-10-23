package com.ecommerce.productservice.messaging;

import com.ecommerce.productservice.event.ReviewEvent;
import com.ecommerce.productservice.service.ProductRatingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes review-events (rating summaries). Errors: retry, then review-events.DLT (KafkaConfig). */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewEventsListener {

    private final ObjectMapper objectMapper;
    private final ProductRatingService productRatingService;

    @KafkaListener(topics = "${app.kafka.topics.review-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onReviewEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received review event key={} partition={} offset={}", record.key(), record.partition(), record.offset());
        productRatingService.apply(objectMapper.readValue(record.value(), ReviewEvent.class));
    }
}

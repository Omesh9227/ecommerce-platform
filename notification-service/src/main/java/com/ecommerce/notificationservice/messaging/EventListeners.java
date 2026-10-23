package com.ecommerce.notificationservice.messaging;

import com.ecommerce.notificationservice.event.OrderEvent;
import com.ecommerce.notificationservice.event.PaymentEvent;
import com.ecommerce.notificationservice.event.WishlistEvent;
import com.ecommerce.notificationservice.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes order-events, payment-events and wishlist-events. Failures are retried, then dead-lettered (see KafkaConfig). */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventListeners {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @KafkaListener(topics = "${app.kafka.topics.order-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received order event key={} offset={}", record.key(), record.offset());
        notificationService.onOrderEvent(objectMapper.readValue(record.value(), OrderEvent.class));
    }

    @KafkaListener(topics = "${app.kafka.topics.payment-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received payment event key={} offset={}", record.key(), record.offset());
        notificationService.onPaymentEvent(objectMapper.readValue(record.value(), PaymentEvent.class));
    }

    @KafkaListener(topics = "${app.kafka.topics.wishlist-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onWishlistEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.debug("Received wishlist event key={} offset={}", record.key(), record.offset());
        notificationService.onWishlistEvent(objectMapper.readValue(record.value(), WishlistEvent.class));
    }
}

package com.ecommerce.orderservice.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * order-service publishes order-events (key = order id) and consumes payment-events and shipment-events.
 * An event that can't be processed (e.g. inventory-service briefly down while confirming a paid order) is
 * retried with growing pauses for about 30 s, then sent to "payment-events.DLT" / "shipment-events.DLT" so it never
 * blocks the partition.
 */
@Configuration
public class KafkaConfig {

    private static final String DLT_SUFFIX = ".DLT";

    @Bean
    public NewTopic orderEventsTopic(KafkaTopicsProperties topics) {
        return TopicBuilder.name(topics.orderEvents()).partitions(topics.partitions()).replicas(1).build();
    }

    @Bean
    public NewTopic paymentEventsDeadLetterTopic(KafkaTopicsProperties topics) {
        return TopicBuilder.name(topics.paymentEvents() + DLT_SUFFIX).partitions(topics.partitions()).replicas(1).build();
    }

    @Bean
    public NewTopic shipmentEventsDeadLetterTopic(KafkaTopicsProperties topics) {
        return TopicBuilder.name(topics.shipmentEvents() + DLT_SUFFIX).partitions(topics.partitions()).replicas(1).build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition()));
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxInterval(16_000L);
        backOff.setMaxElapsedTime(31_000L);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class);
        return handler;
    }
}

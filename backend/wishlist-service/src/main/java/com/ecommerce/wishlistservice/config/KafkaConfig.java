package com.ecommerce.wishlistservice.config;

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
 * wishlist-service publishes wishlist-events (price drops) and consumes product-events. A product event that can't be
 * handled is retried with growing pauses (1 s, 2 s, 4 s ... about 30 s in total), then sent to its own dead-letter
 * topic "product-events.wishlist.DLT" (inventory-service, which also reads product-events, has its own), so it never
 * blocks the partition. Malformed events are not retried.
 */
@Configuration
public class KafkaConfig {

    static final String DLT_SUFFIX = ".wishlist.DLT";

    @Bean
    public NewTopic wishlistEventsTopic(KafkaTopicsProperties topics) {
        return TopicBuilder.name(topics.wishlistEvents()).partitions(topics.partitions()).replicas(1).build();
    }

    @Bean
    public NewTopic productEventsDeadLetterTopic(KafkaTopicsProperties topics) {
        return TopicBuilder.name(topics.productEvents() + DLT_SUFFIX).partitions(topics.partitions()).replicas(1).build();
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

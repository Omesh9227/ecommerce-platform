package com.ecommerce.reviewservice.config;

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
 * review-service publishes review-events (each product's rating summary) and consumes order-events (deliveries make
 * reviews "verified purchase"). An order event that can't be handled is retried with growing pauses (1 s, 2 s, 4 s ...
 * about 30 s in total), then sent to its own dead-letter topic "order-events.review.DLT" (other services reading
 * order-events have their own), so it never blocks the partition. Malformed events are not retried.
 */
@Configuration
public class KafkaConfig {

    static final String DLT_SUFFIX = ".review.DLT";

    @Bean
    public NewTopic reviewEventsTopic(KafkaTopicsProperties topics) {
        return TopicBuilder.name(topics.reviewEvents()).partitions(topics.partitions()).replicas(1).build();
    }

    @Bean
    public NewTopic orderEventsDeadLetterTopic(KafkaTopicsProperties topics) {
        return TopicBuilder.name(topics.orderEvents() + DLT_SUFFIX).partitions(topics.partitions()).replicas(1).build();
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

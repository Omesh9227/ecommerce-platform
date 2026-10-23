package com.ecommerce.notificationservice.config;

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
 * notification-service only consumes (order-, payment- and wishlist-events). An event that can't be handled - mail server down, or a payment event that
 * arrived before its order (different topics, no ordering between them) - is retried with growing pauses for about
 * 30 s, then sent to its own dead-letter topic "&lt;topic&gt;.notification.DLT" (other services consuming the same topics
 * have their own DLTs, so it is always clear whose failure it was). Malformed events are not retried.
 */
@Configuration
public class KafkaConfig {

    static final String DLT_SUFFIX = ".notification.DLT";

    @Bean
    public NewTopic orderEventsDeadLetterTopic(KafkaTopicsProperties topics) {
        return TopicBuilder.name(topics.orderEvents() + DLT_SUFFIX).partitions(topics.partitions()).replicas(1).build();
    }

    @Bean
    public NewTopic paymentEventsDeadLetterTopic(KafkaTopicsProperties topics) {
        return TopicBuilder.name(topics.paymentEvents() + DLT_SUFFIX).partitions(topics.partitions()).replicas(1).build();
    }

    @Bean
    public NewTopic wishlistEventsDeadLetterTopic(KafkaTopicsProperties topics) {
        return TopicBuilder.name(topics.wishlistEvents() + DLT_SUFFIX).partitions(topics.partitions()).replicas(1).build();
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

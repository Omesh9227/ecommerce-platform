package com.ecommerce.paymentservice.config;

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
 * Topics this service owns, and what happens when an order event can't be processed:
 * retried with growing pauses (1 s, 2 s, 4 s ... about 30 s in total) - long enough to ride out a short outage -
 * then sent to "order-events.DLT" so it never blocks the partition. Malformed events are not retried.
 */
@Configuration
public class KafkaConfig {

    private static final String DLT_SUFFIX = ".DLT";

    @Bean
    public NewTopic paymentEventsTopic(KafkaTopicsProperties topics) {
        return TopicBuilder.name(topics.paymentEvents()).partitions(topics.partitions()).replicas(1).build();
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

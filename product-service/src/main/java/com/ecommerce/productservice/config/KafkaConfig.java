package com.ecommerce.productservice.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Topics this service owns, and what happens when a message can't be processed.
 *
 * Failed messages are retried 2 more times (1 s apart); if they still fail they go to "<topic>.DLT"
 * (dead-letter topic) so one bad message never blocks the rest of the partition.
 * Malformed JSON is not retried: it will never succeed.
 */
@Configuration
public class KafkaConfig {

    private static final String DLT_SUFFIX = ".DLT";

    @Bean
    public NewTopic productEventsTopic(KafkaTopicsProperties topics) {
        return TopicBuilder.name(topics.productEvents()).partitions(topics.partitions()).replicas(1).build();
    }

    @Bean
    public NewTopic categoryEventsTopic(KafkaTopicsProperties topics) {
        return TopicBuilder.name(topics.categoryEvents()).partitions(topics.partitions()).replicas(1).build();
    }

    /** Dead-letter topic for inventory events this service could not process. */
    @Bean
    public NewTopic inventoryEventsDeadLetterTopic(KafkaTopicsProperties topics) {
        return TopicBuilder.name(topics.inventoryEvents() + DLT_SUFFIX).partitions(topics.partitions()).replicas(1).build();
    }

    @Bean
    public NewTopic reviewEventsDeadLetterTopic(KafkaTopicsProperties topics) {
        return TopicBuilder.name(topics.reviewEvents() + DLT_SUFFIX).partitions(topics.partitions()).replicas(1).build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        // Explicit name: the default suffix differs between spring-kafka versions (".DLT" vs "-dlt").
        // Same partition as the original record; the DLT has the same number of partitions.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
        handler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class);
        return handler;
    }
}

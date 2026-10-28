package com.ecommerce.adminservice.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * admin-service only consumes (order-, payment-, inventory- and shipment-events). An event that can't be applied is
 * retried with growing pauses (about 30 s in total), then sent to its own dead-letter topic "&lt;topic&gt;.admin.DLT"
 * (other services reading the same topics have their own), so it never blocks the partition. Malformed events are
 * not retried.
 */
@Configuration
public class KafkaConfig {

    static final String DLT_SUFFIX = ".admin.DLT";

    @Bean
    public KafkaAdmin.NewTopics adminDeadLetterTopics(KafkaTopicsProperties topics) {
        return new KafkaAdmin.NewTopics(
                deadLetter(topics.orderEvents(), topics.partitions()),
                deadLetter(topics.paymentEvents(), topics.partitions()),
                deadLetter(topics.inventoryEvents(), topics.partitions()),
                deadLetter(topics.shipmentEvents(), topics.partitions()));
    }

    private static NewTopic deadLetter(String topic, int partitions) {
        return TopicBuilder.name(topic + DLT_SUFFIX).partitions(partitions).replicas(1).build();
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

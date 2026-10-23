package com.ecommerce.orderservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * order-service only publishes (order-events, keyed by order id). payment-service and notification-service
 * will consume these events in later phases.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic orderEventsTopic(KafkaTopicsProperties topics) {
        return TopicBuilder.name(topics.orderEvents()).partitions(topics.partitions()).replicas(1).build();
    }
}

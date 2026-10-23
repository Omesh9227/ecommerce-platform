package com.ecommerce.orderservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** app.kafka.topics.* */
@Validated
@ConfigurationProperties(prefix = "app.kafka.topics")
public record KafkaTopicsProperties(
        @NotBlank String orderEvents,
        @Positive int partitions
) {
}

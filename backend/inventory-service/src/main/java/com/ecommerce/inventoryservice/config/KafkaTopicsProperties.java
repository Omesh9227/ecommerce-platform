package com.ecommerce.inventoryservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** app.kafka.topics.* - topic names shared with product-service. */
@Validated
@ConfigurationProperties(prefix = "app.kafka.topics")
public record KafkaTopicsProperties(
        @NotBlank String productEvents,
        @NotBlank String inventoryEvents,
        @Positive int partitions
) {
}

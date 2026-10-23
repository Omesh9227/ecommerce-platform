package com.ecommerce.paymentservice.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** app.outbox.* - how the outbox relay sends saved events to Kafka. */
@Validated
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        @Positive long pollIntervalMs,
        @Positive int batchSize,
        @Positive long sendTimeoutSeconds
) {
}

package com.ecommerce.notificationservice.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** app.notification.* - how emails look and who they come from. */
@Validated
@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(
        @NotBlank @Email String fromAddress,
        @NotBlank String fromName,
        @NotBlank String storeName,
        @NotBlank @Email String supportEmail,
        /* Shown in the "order placed" email; must match order-service's app.order.payment-window. */
        @Positive int paymentWindowMinutes
) {
}

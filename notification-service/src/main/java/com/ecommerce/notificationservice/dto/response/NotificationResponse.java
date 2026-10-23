package com.ecommerce.notificationservice.dto.response;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String eventType,
        String orderNumber,
        UUID userId,
        String channel,
        String recipient,
        String subject,
        String status,
        Instant sentAt
) {
}

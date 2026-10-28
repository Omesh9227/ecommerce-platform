package com.ecommerce.paymentservice.outbox;

import com.ecommerce.paymentservice.config.KafkaTopicsProperties;
import com.ecommerce.paymentservice.entity.Payment;
import com.ecommerce.paymentservice.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Records payment events in the outbox (same transaction as the payment change). Flush the payment first. */
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final OutboxWriter outboxWriter;
    private final KafkaTopicsProperties topics;

    public void publish(String eventType, Payment payment, String reason, Instant now) {
        outboxWriter.write(topics.paymentEvents(), payment.getOrderId().toString(), eventType,
                PaymentEvent.of(eventType, payment, reason, now));
    }
}

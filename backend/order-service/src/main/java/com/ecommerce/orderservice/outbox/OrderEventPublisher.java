package com.ecommerce.orderservice.outbox;

import com.ecommerce.orderservice.config.KafkaTopicsProperties;
import com.ecommerce.orderservice.entity.CustomerOrder;
import com.ecommerce.orderservice.event.OrderEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Records order events in the outbox (same transaction as the order change). Key = order id. */
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final OutboxWriter outboxWriter;
    private final KafkaTopicsProperties topics;

    public void publish(String eventType, CustomerOrder order, Instant now) {
        outboxWriter.write(topics.orderEvents(), order.getId().toString(), eventType, OrderEvent.of(eventType, order, now));
    }
}

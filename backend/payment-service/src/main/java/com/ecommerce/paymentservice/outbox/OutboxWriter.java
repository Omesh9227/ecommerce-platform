package com.ecommerce.paymentservice.outbox;

import com.ecommerce.paymentservice.entity.OutboxEvent;
import com.ecommerce.paymentservice.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves an event to the outbox table. MANDATORY propagation: it must join the caller's transaction,
 * so the event is stored if and only if the data change is committed.
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(String topic, String key, String eventType, Object event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialise " + eventType + " event", ex);
        }
        outboxEventRepository.save(new OutboxEvent(topic, key, eventType, payload));
    }
}

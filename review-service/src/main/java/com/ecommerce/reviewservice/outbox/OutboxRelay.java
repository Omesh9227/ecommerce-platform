package com.ecommerce.reviewservice.outbox;

import com.ecommerce.reviewservice.config.OutboxProperties;
import com.ecommerce.reviewservice.entity.OutboxEvent;
import com.ecommerce.reviewservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Sends pending outbox events to Kafka, oldest first, and marks them published.
 *
 * Delivery is at-least-once: if the service stops after Kafka accepted an event but before it was marked,
 * the event is sent again on restart, so consumers must ignore duplicates (each event has a unique eventId).
 * On the first failure the batch stops, so one customer's events are never sent out of order.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;

    /** @return number of events published in this run */
    @Transactional
    public int publishPending() {
        List<OutboxEvent> batch = outboxEventRepository.lockNextBatch(properties.batchSize());
        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload())
                        .get(properties.sendTimeoutSeconds(), TimeUnit.SECONDS);
                event.markPublished();
                published++;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                event.markFailed("Interrupted");
                break;
            } catch (Exception ex) {
                event.markFailed(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                log.warn("Outbox: could not publish {} {} (attempt {}), will retry: {}",
                        event.getEventType(), event.getId(), event.getAttempts(), ex.getMessage());
                break;
            }
        }
        if (published > 0) {
            log.debug("Outbox: published {} event(s)", published);
        }
        return published;
    }
}

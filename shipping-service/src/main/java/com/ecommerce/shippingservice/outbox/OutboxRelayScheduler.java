package com.ecommerce.shippingservice.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Triggers the outbox relay every app.outbox.poll-interval-ms (separate bean so the relay's @Transactional applies). */
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxRelay outboxRelay;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms}", initialDelayString = "${app.outbox.poll-interval-ms}")
    public void relay() {
        outboxRelay.publishPending();
    }
}

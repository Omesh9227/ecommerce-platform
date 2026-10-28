package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.entity.CustomerOrder;
import com.ecommerce.orderservice.entity.OrderStatus;
import com.ecommerce.orderservice.entity.OrderStatusHistory;
import com.ecommerce.orderservice.event.EventTypes;
import com.ecommerce.orderservice.outbox.OrderEventPublisher;
import com.ecommerce.orderservice.repository.CustomerOrderRepository;
import com.ecommerce.orderservice.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * The one place a status change happens, so every change is recorded the same way:
 * status updated and flushed (version incremented) -> history row -> event in the outbox. All in the caller's transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTransitions {

    private final CustomerOrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderEventPublisher eventPublisher;

    /** Records the creation of a new order (status CREATED). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCreated(CustomerOrder order, UUID actorId, Instant now) {
        historyRepository.save(new OrderStatusHistory(order.getId(), null, OrderStatus.CREATED, actorId,
                "Checkout started", now));
    }

    /** Adds a timeline entry without changing the status (e.g. "refund completed"). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void note(CustomerOrder order, UUID actorId, String note, Instant now) {
        historyRepository.save(new OrderStatusHistory(order.getId(), order.getStatus(), order.getStatus(), actorId, note, now));
        log.info("Order {} note: {}", order.getOrderNumber(), note);
    }

    /**
     * @param actorId who made the change; null for the system
     * @param note    shown in the timeline; for CANCELLED / CHECKOUT_FAILED it is also the order's statusReason
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(CustomerOrder order, OrderStatus next, UUID actorId, String note, Instant now) {
        OrderStatus from = order.getStatus();
        order.moveTo(next, now, note);
        orderRepository.saveAndFlush(order);
        historyRepository.save(new OrderStatusHistory(order.getId(), from, next, actorId, note, now));
        EventTypes.forStatus(next).ifPresent(type -> eventPublisher.publish(type, order, now));
        log.info("Order {} {} -> {} by {}{}", order.getOrderNumber(), from, next,
                actorId == null ? "system" : actorId, note == null ? "" : " (" + note + ")");
    }
}

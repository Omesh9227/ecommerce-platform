package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.config.OrderProperties;
import com.ecommerce.orderservice.entity.CustomerOrder;
import com.ecommerce.orderservice.entity.OrderStatus;
import com.ecommerce.orderservice.repository.CustomerOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * The short database transactions of a checkout. They are kept separate from the calls to other services
 * (cart, product, inventory) so no database transaction or lock is held while waiting on the network.
 */
@Component
@RequiredArgsConstructor
public class CheckoutPersistence {

    private final CustomerOrderRepository orderRepository;
    private final OrderTransitions transitions;
    private final OrderProperties orderProperties;

    /** @throws org.springframework.dao.DataIntegrityViolationException if this checkout's order already exists */
    @Transactional
    public CustomerOrder insertNew(CustomerOrder order, Instant now) {
        CustomerOrder saved = orderRepository.saveAndFlush(order);
        transitions.recordCreated(saved, saved.getUserId(), now);
        return saved;
    }

    /**
     * CREATED -> PENDING_PAYMENT once stock is reserved.
     *
     * @return true if this call placed the order; false if it was already placed (a concurrent retry got there first)
     */
    @Transactional
    public boolean markPlaced(UUID orderId, Instant now) {
        CustomerOrder order = orderRepository.findByIdForUpdate(orderId).orElseThrow();
        if (order.getStatus() != OrderStatus.CREATED) {
            return false;
        }
        order.setPaymentDueAt(now.plus(orderProperties.paymentWindow()));
        transitions.apply(order, OrderStatus.PENDING_PAYMENT, order.getUserId(), "Order placed, stock reserved", now);
        return true;
    }

    /** CREATED -> CHECKOUT_FAILED (e.g. not enough stock). No-op if the order already moved on. */
    @Transactional
    public void markFailed(UUID orderId, String reason, Instant now) {
        CustomerOrder order = orderRepository.findByIdForUpdate(orderId).orElseThrow();
        if (order.getStatus() == OrderStatus.CREATED) {
            transitions.apply(order, OrderStatus.CHECKOUT_FAILED, null, reason, now);
        }
    }
}

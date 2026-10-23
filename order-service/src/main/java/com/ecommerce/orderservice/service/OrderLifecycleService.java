package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.client.DownstreamRejectedException;
import com.ecommerce.orderservice.client.InventoryClient;
import com.ecommerce.orderservice.config.OrderProperties;
import com.ecommerce.orderservice.dto.request.ShipOrderRequest;
import com.ecommerce.orderservice.dto.response.OrderResponse;
import com.ecommerce.orderservice.entity.CustomerOrder;
import com.ecommerce.orderservice.entity.OrderStatus;
import com.ecommerce.orderservice.exception.ConflictException;
import com.ecommerce.orderservice.repository.CustomerOrderRepository;
import com.ecommerce.orderservice.repository.OrderStatusHistoryRepository;
import com.ecommerce.orderservice.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Status changes after checkout. Each one locks the order row for its whole transaction, including the call to
 * inventory-service, so a cancel and a payment confirmation (or the expiry job) for the same order can never
 * both happen. If inventory-service fails, the transaction rolls back and the order is unchanged.
 * Repeating a change that already happened returns the order as it is (safe retries).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderLifecycleService {

    static final String EXPIRED_REASON = "Payment was not received in time";
    static final String STUCK_REASON = "Checkout could not be completed. Please try again.";

    private final CustomerOrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderTransitions transitions;
    private final InventoryClient inventoryClient;
    private final OrderQueryService queryService;
    private final OrderProperties orderProperties;
    private final Clock clock;

    // ---------- Customer or admin ----------

    @Transactional
    public OrderResponse cancel(String orderNumber, AuthenticatedUser caller, String reason) {
        CustomerOrder order = lockVisible(orderNumber, caller);
        if (order.getStatus() != OrderStatus.CANCELLED) {
            requireMove(order, OrderStatus.CANCELLED, order.getStatus() == OrderStatus.SHIPPED
                    ? " (it is already on its way)" : "");
            boolean paid = order.getStatus() == OrderStatus.CONFIRMED;
            if (paid) {
                returnStock(order);   // sold units go back on hand; payment-service refunds on ORDER_CANCELLED
            } else {
                releaseStock(order);
            }
            String note = reason != null && !reason.isBlank() ? reason.trim()
                    : caller.isAdmin() && !order.getUserId().equals(caller.id()) ? "Cancelled by admin" : "Cancelled by customer";
            if (paid) {
                note += " - a full refund has been requested";
            }
            transitions.apply(order, OrderStatus.CANCELLED, caller.id(), note, clock.instant());
        }
        return queryService.toResponse(order, caller.isAdmin());
    }

    // ---------- Admin ----------

    @Transactional
    public OrderResponse ship(String orderNumber, AuthenticatedUser admin, ShipOrderRequest request) {
        CustomerOrder order = lockVisible(orderNumber, admin);
        if (order.getStatus() != OrderStatus.SHIPPED) {
            requireMove(order, OrderStatus.SHIPPED, "");
            if (request != null) {
                order.setShipment(blankToNull(request.carrier()), blankToNull(request.trackingNumber()));
            }
            String note = order.getCarrier() == null ? "Shipped"
                    : "Shipped with " + order.getCarrier() + (order.getTrackingNumber() == null ? "" : " (" + order.getTrackingNumber() + ")");
            transitions.apply(order, OrderStatus.SHIPPED, admin.id(), note, clock.instant());
        }
        return queryService.toResponse(order, true);
    }

    @Transactional
    public OrderResponse deliver(String orderNumber, AuthenticatedUser admin) {
        CustomerOrder order = lockVisible(orderNumber, admin);
        if (order.getStatus() != OrderStatus.DELIVERED) {
            requireMove(order, OrderStatus.DELIVERED, "");
            transitions.apply(order, OrderStatus.DELIVERED, admin.id(), "Delivered", clock.instant());
        }
        return queryService.toResponse(order, true);
    }

    // ---------- System: payment-service events ----------

    /**
     * PAYMENT_SUCCEEDED: PENDING_PAYMENT -> CONFIRMED, and the reserved stock becomes a sale.
     * Safe to receive twice. If the order was cancelled meanwhile (payment window ended just as the customer paid),
     * nothing happens here: payment-service sees the ORDER_CANCELLED event and refunds the payment.
     *
     * @return true if the order was confirmed now
     */
    @Transactional
    public boolean markPaid(UUID orderId, String note) {
        CustomerOrder order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null) {
            log.warn("PAYMENT_SUCCEEDED for unknown order {}", orderId);
            return false;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            log.info("Order {} is {}; payment confirmation ignored", order.getOrderNumber(), order.getStatus());
            return false;
        }
        try {
            inventoryClient.confirm(order.getOrderNumber());
        } catch (DownstreamRejectedException ex) {
            // Should not happen (the reservation lives as long as the order waits for payment). Retried, then dead-lettered.
            throw new IllegalStateException("Stock for paid order " + order.getOrderNumber() + " can't be confirmed: " + ex.getMessage());
        }
        transitions.apply(order, OrderStatus.CONFIRMED, null, note, clock.instant());
        return true;
    }

    /** PAYMENT_REFUNDED: adds the refund to the cancelled order's timeline (once). */
    @Transactional
    public void recordRefund(UUID orderId, String note) {
        orderRepository.findByIdForUpdate(orderId).ifPresent(order -> {
            boolean alreadyRecorded = historyRepository.findByOrderIdOrderByChangedAtAsc(orderId).stream()
                    .anyMatch(h -> note.equals(h.getNote()));
            if (!alreadyRecorded) {
                transitions.note(order, null, note, clock.instant());
            }
        });
    }

    // ---------- System (OrderExpiryJob) ----------

    /** Cancels one order whose payment window has passed. @return true if it was cancelled now */
    @Transactional
    public boolean expireUnpaid(UUID orderId) {
        Instant now = clock.instant();
        CustomerOrder order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT
                || order.getPaymentDueAt() == null || order.getPaymentDueAt().isAfter(now)) {
            return false; // paid or cancelled in the meantime
        }
        releaseStock(order);
        transitions.apply(order, OrderStatus.CANCELLED, null, EXPIRED_REASON, now);
        return true;
    }

    /** Fails one checkout that stayed in CREATED too long, releasing any stock it may have reserved. */
    @Transactional
    public boolean failStuckCheckout(UUID orderId) {
        Instant now = clock.instant();
        CustomerOrder order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.CREATED
                || order.getCreatedAt().isAfter(now.minus(orderProperties.stuckCheckoutTimeout()))) {
            return false;
        }
        releaseStock(order);
        transitions.apply(order, OrderStatus.CHECKOUT_FAILED, null, STUCK_REASON, now);
        return true;
    }

    // ---------- Helpers ----------

    private CustomerOrder lockVisible(String orderNumber, AuthenticatedUser caller) {
        return orderRepository.findByOrderNumberForUpdate(orderNumber)
                .filter(order -> OrderQueryService.canSee(order, caller))
                .orElseThrow(() -> OrderQueryService.notFound(orderNumber));
    }

    private static void requireMove(CustomerOrder order, OrderStatus next, String hint) {
        if (!order.getStatus().canMoveTo(next)) {
            throw new ConflictException("Order " + order.getOrderNumber() + " is " + order.getStatus()
                    + " and can't be moved to " + next + hint);
        }
    }

    /** Puts a paid order's sold units back on hand. */
    private void returnStock(CustomerOrder order) {
        try {
            inventoryClient.returnStock(order.getOrderNumber());
        } catch (DownstreamRejectedException ex) {
            throw new ConflictException("Stock for order " + order.getOrderNumber() + " can't be returned: " + ex.getMessage());
        }
    }

    /** Gives reserved stock back. A reservation that doesn't exist (never made) is fine. */
    private void releaseStock(CustomerOrder order) {
        try {
            inventoryClient.release(order.getOrderNumber());
        } catch (DownstreamRejectedException ex) {
            if (!ex.isNotFound()) {
                throw new ConflictException("Stock for order " + order.getOrderNumber() + " can't be released: " + ex.getMessage());
            }
            log.debug("No reservation to release for {}", order.getOrderNumber());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

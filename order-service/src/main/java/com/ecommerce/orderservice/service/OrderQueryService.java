package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.dto.response.OrderResponse;
import com.ecommerce.orderservice.dto.response.OrderSummaryResponse;
import com.ecommerce.orderservice.dto.response.PageResponse;
import com.ecommerce.orderservice.entity.CustomerOrder;
import com.ecommerce.orderservice.entity.OrderStatus;
import com.ecommerce.orderservice.entity.OrderStatusHistory;
import com.ecommerce.orderservice.exception.BadRequestException;
import com.ecommerce.orderservice.exception.ResourceNotFoundException;
import com.ecommerce.orderservice.mapper.OrderMapper;
import com.ecommerce.orderservice.repository.CustomerOrderRepository;
import com.ecommerce.orderservice.repository.OrderSpecifications;
import com.ecommerce.orderservice.repository.OrderStatusHistoryRepository;
import com.ecommerce.orderservice.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reading orders. Customers see only their own orders and never internal ones (CREATED, CHECKOUT_FAILED):
 * anything else is a 404, so order numbers of other customers can't even be confirmed to exist.
 */
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "totalAmount", "status");
    static final Set<OrderStatus> CUSTOMER_VISIBLE = EnumSet.copyOf(
            Arrays.stream(OrderStatus.values()).filter(OrderStatus::isVisibleToCustomer).toList());

    private final CustomerOrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderMapper mapper;

    @Transactional(readOnly = true)
    public Optional<CustomerOrder> findById(UUID orderId) {
        return orderRepository.findWithItemsById(orderId);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderNumber, AuthenticatedUser caller) {
        CustomerOrder order = orderRepository.findByOrderNumber(orderNumber)
                .filter(o -> canSee(o, caller))
                .orElseThrow(() -> notFound(orderNumber));
        return toResponse(order, caller.isAdmin());
    }

    @Transactional(readOnly = true)
    public OrderResponse toResponse(UUID orderId, boolean includeInternalSteps) {
        return toResponse(orderRepository.findById(orderId).orElseThrow(), includeInternalSteps);
    }

    /** Maps an order loaded in the current transaction. */
    @Transactional(readOnly = true)
    public OrderResponse toResponse(CustomerOrder order, boolean includeInternalSteps) {
        List<OrderStatusHistory> history = historyRepository.findByOrderIdOrderByChangedAtAsc(order.getId());
        if (!includeInternalSteps) {
            history = history.stream().filter(h -> h.getToStatus().isVisibleToCustomer()).toList();
        }
        return mapper.toResponse(order, history);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> myOrders(UUID userId, OrderStatus status, Pageable pageable) {
        validateSort(pageable.getSort());
        Set<OrderStatus> statuses = status == null ? CUSTOMER_VISIBLE
                : CUSTOMER_VISIBLE.contains(status) ? Set.of(status) : Set.of();
        if (statuses.isEmpty()) {
            return PageResponse.from(Page.empty(pageable));
        }
        return PageResponse.from(orderRepository.findByUserIdAndStatusIn(userId, statuses, pageable).map(mapper::toSummary));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> allOrders(OrderStatus status, UUID userId, Pageable pageable) {
        validateSort(pageable.getSort());
        return PageResponse.from(orderRepository.findAll(OrderSpecifications.withFilters(status, userId), pageable)
                .map(mapper::toSummary));
    }

    static boolean canSee(CustomerOrder order, AuthenticatedUser caller) {
        return caller.isAdmin()
                || (order.getUserId().equals(caller.id()) && order.getStatus().isVisibleToCustomer());
    }

    static ResourceNotFoundException notFound(String orderNumber) {
        return new ResourceNotFoundException("Order not found: " + orderNumber);
    }

    private static void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new BadRequestException("Cannot sort by '" + order.getProperty() + "'. Allowed: "
                        + String.join(", ", SORTABLE_FIELDS.stream().sorted().toList()));
            }
        }
    }
}

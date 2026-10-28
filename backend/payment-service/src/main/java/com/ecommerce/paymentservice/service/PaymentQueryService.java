package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.dto.response.PageResponse;
import com.ecommerce.paymentservice.dto.response.PaymentResponse;
import com.ecommerce.paymentservice.dto.response.PaymentSummaryResponse;
import com.ecommerce.paymentservice.entity.PaymentStatus;
import com.ecommerce.paymentservice.exception.ResourceNotFoundException;
import com.ecommerce.paymentservice.mapper.PaymentMapper;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import com.ecommerce.paymentservice.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customers see only payments of their own orders (others are a 404); admins see all. */
@Service
@RequiredArgsConstructor
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper mapper;

    @Transactional(readOnly = true)
    public PaymentResponse forOrder(String orderNumber, AuthenticatedUser caller) {
        return paymentRepository.findByOrderNumber(orderNumber)
                .filter(p -> caller.isAdmin() || p.getUserId().equals(caller.id()))
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for order " + orderNumber));
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentSummaryResponse> all(PaymentStatus status, Pageable pageable) {
        return PageResponse.from((status == null ? paymentRepository.findAll(pageable)
                : paymentRepository.findByStatus(status, pageable)).map(mapper::toSummary));
    }
}

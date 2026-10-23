package com.ecommerce.notificationservice.service;

import com.ecommerce.notificationservice.dto.response.NotificationResponse;
import com.ecommerce.notificationservice.dto.response.PageResponse;
import com.ecommerce.notificationservice.entity.Notification;
import com.ecommerce.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> forUser(UUID userId, Pageable pageable) {
        return PageResponse.from(notificationRepository.findByUserId(userId, pageable).map(NotificationQueryService::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> all(String orderNumber, Pageable pageable) {
        return PageResponse.from((orderNumber == null || orderNumber.isBlank()
                ? notificationRepository.findAll(pageable)
                : notificationRepository.findByOrderNumber(orderNumber.trim(), pageable)).map(NotificationQueryService::toResponse));
    }

    static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getEventType(), n.getOrderNumber(), n.getUserId(), n.getChannel(),
                n.getRecipient(), n.getSubject(), n.getStatus(), n.getSentAt());
    }
}

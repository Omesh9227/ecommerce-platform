package com.ecommerce.notificationservice.controller;

import com.ecommerce.notificationservice.dto.response.ApiResult;
import com.ecommerce.notificationservice.dto.response.NotificationResponse;
import com.ecommerce.notificationservice.dto.response.PageResponse;
import com.ecommerce.notificationservice.security.AuthenticatedUser;
import com.ecommerce.notificationservice.service.NotificationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Emails sent to you about your orders and payments")
public class NotificationController {

    private final NotificationQueryService queryService;

    @Operation(summary = "My notifications", description = "Newest first.")
    @GetMapping
    public ResponseEntity<ApiResult<PageResponse<NotificationResponse>>> mine(
            @ParameterObject @PageableDefault(size = 20, sort = "sentAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Notifications fetched successfully", queryService.forUser(currentUser.id(), pageable)));
    }

    @Operation(summary = "All notifications (ADMIN)", description = "Optionally for one order. Newest first.")
    @GetMapping("/admin")
    public ResponseEntity<ApiResult<PageResponse<NotificationResponse>>> all(
            @RequestParam(name = "orderNumber", required = false) String orderNumber,
            @ParameterObject @PageableDefault(size = 20, sort = "sentAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResult.of("Notifications fetched successfully", queryService.all(orderNumber, pageable)));
    }
}

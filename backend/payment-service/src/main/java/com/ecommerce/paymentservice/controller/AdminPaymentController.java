package com.ecommerce.paymentservice.controller;

import com.ecommerce.paymentservice.dto.response.ApiResult;
import com.ecommerce.paymentservice.dto.response.PageResponse;
import com.ecommerce.paymentservice.dto.response.PaymentSummaryResponse;
import com.ecommerce.paymentservice.entity.PaymentStatus;
import com.ecommerce.paymentservice.service.PaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/admin")
@RequiredArgsConstructor
@Tag(name = "Payments (admin)", description = "All payments (ADMIN)")
public class AdminPaymentController {

    private final PaymentQueryService queryService;

    @Operation(summary = "All payments", description = "Filter by status. Newest first.")
    @GetMapping
    public ResponseEntity<ApiResult<PageResponse<PaymentSummaryResponse>>> all(
            @RequestParam(name = "status", required = false) PaymentStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResult.of("Payments fetched successfully", queryService.all(status, pageable)));
    }
}

package com.ecommerce.inventoryservice.controller;

import com.ecommerce.inventoryservice.dto.request.AdjustStockRequest;
import com.ecommerce.inventoryservice.dto.request.SetStockRequest;
import com.ecommerce.inventoryservice.dto.request.ThresholdRequest;
import com.ecommerce.inventoryservice.dto.response.ApiResult;
import com.ecommerce.inventoryservice.dto.response.ErrorResponse;
import com.ecommerce.inventoryservice.dto.response.InventoryItemResponse;
import com.ecommerce.inventoryservice.dto.response.PageResponse;
import com.ecommerce.inventoryservice.dto.response.StockMovementResponse;
import com.ecommerce.inventoryservice.security.AuthenticatedUser;
import com.ecommerce.inventoryservice.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Stock levels per product (ADMIN). Items are created automatically when a product is created.")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Not logged in",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Not an ADMIN",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class InventoryController {

    private final InventoryService inventoryService;

    @Operation(summary = "List stock", description = "Filter with lowStock=true to see products at or below their threshold. "
            + "search matches SKU or product name. Sortable: sku, productName, quantityOnHand, quantityReserved, "
            + "lowStockThreshold, updatedAt.")
    @GetMapping
    public ResponseEntity<ApiResult<PageResponse<InventoryItemResponse>>> list(
            @RequestParam(name = "lowStock", required = false) Boolean lowStock,
            @RequestParam(name = "search", required = false) String search,
            @ParameterObject @PageableDefault(size = 20, sort = "sku", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResult.of("Inventory fetched successfully",
                inventoryService.list(lowStock, search, pageable)));
    }

    @Operation(summary = "Get stock for one product")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock returned"),
            @ApiResponse(responseCode = "404", description = "No inventory item for this product",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResult<InventoryItemResponse>> getItem(@PathVariable("productId") UUID productId) {
        return ResponseEntity.ok(ApiResult.of("Inventory item fetched successfully", inventoryService.getItem(productId)));
    }

    @Operation(summary = "Set on-hand stock to an exact number", description = "Cannot go below the reserved quantity.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed or below reserved quantity",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No inventory item for this product",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{productId}/stock")
    public ResponseEntity<ApiResult<InventoryItemResponse>> setStock(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody SetStockRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Stock updated successfully",
                inventoryService.setStock(productId, request, currentUser.id())));
    }

    @Operation(summary = "Add or remove stock", description = "Positive quantityChange = restock, negative = removal.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock adjusted"),
            @ApiResponse(responseCode = "400", description = "Validation failed, 0 change, or would go below reserved",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No inventory item for this product",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{productId}/stock/adjustments")
    public ResponseEntity<ApiResult<InventoryItemResponse>> adjustStock(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody AdjustStockRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Stock adjusted successfully",
                inventoryService.adjustStock(productId, request, currentUser.id())));
    }

    @Operation(summary = "Change the low-stock threshold")
    @PutMapping("/{productId}/threshold")
    public ResponseEntity<ApiResult<InventoryItemResponse>> updateThreshold(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody ThresholdRequest request) {
        return ResponseEntity.ok(ApiResult.of("Threshold updated successfully",
                inventoryService.updateThreshold(productId, request)));
    }

    @Operation(summary = "Stock movement history for a product", description = "Newest first.")
    @GetMapping("/{productId}/movements")
    public ResponseEntity<ApiResult<PageResponse<StockMovementResponse>>> movements(
            @PathVariable("productId") UUID productId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResult.of("Stock movements fetched successfully",
                inventoryService.movements(productId, pageable)));
    }
}

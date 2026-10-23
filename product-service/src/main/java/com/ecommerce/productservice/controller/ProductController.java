package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.config.OpenApiConfig;
import com.ecommerce.productservice.dto.request.ProductRequest;
import com.ecommerce.productservice.dto.request.ProductSearchCriteria;
import com.ecommerce.productservice.dto.response.ApiResult;
import com.ecommerce.productservice.dto.response.ErrorResponse;
import com.ecommerce.productservice.dto.response.PageResponse;
import com.ecommerce.productservice.dto.response.ProductResponse;
import com.ecommerce.productservice.entity.ProductStatus;
import com.ecommerce.productservice.security.AuthenticatedUser;
import com.ecommerce.productservice.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Browsing is public; changes need ADMIN.")
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Search and list products",
            description = "Paginated, newest first by default. Customers only see ACTIVE products. "
                    + "categoryId includes sub-categories (Electronics also returns Mobiles). "
                    + "Price filters use the selling price. inStock filters on availability from inventory-service. "
                    + "Sortable: name, brand, price, sellingPrice, availableQuantity, createdAt, updatedAt. "
                    + "Example: ?search=iphone&categoryId=...&minPrice=10000&maxPrice=80000&sort=sellingPrice,asc")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Products returned"),
            @ApiResponse(responseCode = "400", description = "Invalid filter or sort",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ApiResult<PageResponse<ProductResponse>>> search(
            @Parameter(description = "Text search in name, brand and SKU") @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @RequestParam(name = "brand", required = false) String brand,
            @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
            @Parameter(description = "ADMIN only; ignored for customers")
            @RequestParam(name = "status", required = false) ProductStatus status,
            @Parameter(description = "true = only products with stock, false = only out of stock")
            @RequestParam(name = "inStock", required = false) Boolean inStock,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {

        ProductSearchCriteria criteria = new ProductSearchCriteria(search, categoryId, brand, minPrice, maxPrice, status, inStock);
        return ResponseEntity.ok(ApiResult.of("Products fetched successfully",
                productService.search(criteria, pageable, AuthenticatedUser.isAdmin(currentUser))));
    }

    @Operation(summary = "Get a product by id", description = "Customers get 404 for products that are not ACTIVE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResult<ProductResponse>> getProduct(
            @PathVariable("id") UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Product fetched successfully",
                productService.getProduct(id, AuthenticatedUser.isAdmin(currentUser))));
    }

    @Operation(summary = "Create a product (ADMIN)",
            description = "SKU is stored upper-case and must be unique. sellingPrice = price - discount%.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created"),
            @ApiResponse(responseCode = "400", description = "Validation failed or category not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not logged in",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "SKU already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ApiResult<ProductResponse>> create(
            @Valid @RequestBody ProductRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("Product created successfully", productService.create(request, currentUser.id())));
    }

    @Operation(summary = "Update a product (ADMIN)", description = "Replaces all fields; status is kept if omitted.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed or category not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Product not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "SKU already used by another product",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResult<ProductResponse>> update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ProductRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResult.of("Product updated successfully",
                productService.update(id, request, currentUser.id())));
    }

    @Operation(summary = "Delete a product (ADMIN)",
            description = "Soft delete: status becomes DISCONTINUED and customers no longer see it. "
                    + "Set status back to ACTIVE with PUT to restore it.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Product discontinued"),
            @ApiResponse(responseCode = "404", description = "Product not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable("id") UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        productService.delete(id, currentUser.id());
        return ResponseEntity.noContent().build();
    }
}

package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.config.OpenApiConfig;
import com.ecommerce.productservice.dto.request.CategoryRequest;
import com.ecommerce.productservice.dto.response.ApiResult;
import com.ecommerce.productservice.dto.response.CategoryResponse;
import com.ecommerce.productservice.dto.response.CategoryTreeNode;
import com.ecommerce.productservice.dto.response.ErrorResponse;
import com.ecommerce.productservice.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Category tree. Reading is public; changes need ADMIN.")
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "Get the full category tree", description = "Top-level categories with nested sub-categories, sorted by name.")
    @ApiResponse(responseCode = "200", description = "Category tree")
    @GetMapping
    public ResponseEntity<ApiResult<List<CategoryTreeNode>>> getTree() {
        return ResponseEntity.ok(ApiResult.of("Categories fetched successfully", categoryService.getTree()));
    }

    @Operation(summary = "Get one category with its parent and direct sub-categories")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category found"),
            @ApiResponse(responseCode = "404", description = "Category not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResult<CategoryResponse>> getCategory(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResult.of("Category fetched successfully", categoryService.getCategory(id)));
    }

    @Operation(summary = "Create a category (ADMIN)", description = "Omit parentId for a top-level category.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Category created"),
            @ApiResponse(responseCode = "400", description = "Validation failed or parent not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not logged in",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Name already used at this level",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ApiResult<CategoryResponse>> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of("Category created successfully", categoryService.create(request)));
    }

    @Operation(summary = "Update or move a category (ADMIN)",
            description = "Set parentId to move it; null makes it top-level. It cannot be moved under its own sub-category.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed, parent not found or circular move",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Category not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Name already used at this level",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResult<CategoryResponse>> update(@PathVariable("id") UUID id,
                                                              @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResult.of("Category updated successfully", categoryService.update(id, request)));
    }

    @Operation(summary = "Delete a category (ADMIN)", description = "Only allowed when it has no sub-categories and no products.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Category deleted"),
            @ApiResponse(responseCode = "404", description = "Category not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Category still has sub-categories or products",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

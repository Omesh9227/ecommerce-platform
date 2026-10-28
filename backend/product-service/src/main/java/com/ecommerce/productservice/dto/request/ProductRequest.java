package com.ecommerce.productservice.dto.request;

import com.ecommerce.productservice.entity.ProductStatus;
import com.ecommerce.productservice.util.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Used for both create (POST) and update (PUT). On PUT every field is replaced,
 * except status: if omitted, the current status is kept.
 */
@Schema(description = "Create or update a product")
public record ProductRequest(

        @Schema(example = "IPHONE-15-128-BLK")
        @NotBlank(message = "SKU is required")
        @Pattern(regexp = ValidationPatterns.SKU, message = "SKU must be 3-64 letters, digits or hyphens")
        String sku,

        @Schema(example = "Apple iPhone 15 (128 GB) - Black")
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 200, message = "Name must be 2-200 characters")
        String name,

        @Schema(example = "Dynamic Island, 48MP main camera, USB-C")
        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @Schema(example = "Apple")
        @Size(max = 100, message = "Brand must be at most 100 characters")
        String brand,

        @NotNull(message = "Category is required")
        UUID categoryId,

        @Schema(example = "79900.00", description = "List price (MRP) in INR")
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Price can have at most 10 digits and 2 decimals")
        BigDecimal price,

        @Schema(example = "10", description = "Discount in percent (0 to below 100). Defaults to 0.")
        @DecimalMin(value = "0.00", message = "Discount cannot be negative")
        @DecimalMax(value = "100.00", inclusive = false, message = "Discount must be less than 100")
        @Digits(integer = 3, fraction = 2, message = "Discount can have at most 2 decimals")
        BigDecimal discountPercent,

        @Schema(example = "ACTIVE", description = "Defaults to ACTIVE on create; unchanged on update if omitted")
        ProductStatus status,

        @Schema(example = "[\"https://cdn.example.com/iphone15-front.jpg\"]")
        @Size(max = 10, message = "At most 10 image URLs are allowed")
        List<@NotBlank(message = "Image URL must not be blank")
             @Size(max = 500, message = "Image URL must be at most 500 characters")
             @Pattern(regexp = ValidationPatterns.IMAGE_URL, message = "Image URL must start with http:// or https://")
             String> imageUrls
) {
}

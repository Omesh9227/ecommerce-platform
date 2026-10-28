package com.ecommerce.paymentservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Card details. Used for one charge and then forgotten: never stored, never logged. */
@Schema(description = "Card details (test cards: 4242 4242 4242 4242 succeeds, 4000 0000 0000 0002 is declined)")
public record CardRequest(

        @Schema(example = "4242 4242 4242 4242")
        @NotBlank(message = "card.number is required")
        @Pattern(regexp = "^[0-9 -]{12,23}$", message = "card.number must contain 12-19 digits")
        String number,

        @Schema(example = "12")
        @NotNull(message = "card.expiryMonth is required")
        @Min(value = 1, message = "card.expiryMonth must be 1-12")
        @Max(value = 12, message = "card.expiryMonth must be 1-12")
        Integer expiryMonth,

        @Schema(example = "2030")
        @NotNull(message = "card.expiryYear is required")
        @Min(value = 2000, message = "card.expiryYear must be a 4-digit year")
        @Max(value = 2100, message = "card.expiryYear must be a 4-digit year")
        Integer expiryYear,

        @Schema(example = "123")
        @NotBlank(message = "card.cvv is required")
        @Pattern(regexp = "^[0-9]{3,4}$", message = "card.cvv must be 3 or 4 digits")
        String cvv,

        @Schema(example = "Omesh Kumar")
        @Size(max = 100, message = "card.holderName must be at most 100 characters")
        String holderName
) {

    @Override
    public String toString() { // records print every field by default - card data must never reach a log
        return "CardRequest[number=****, cvv=***]";
    }
}

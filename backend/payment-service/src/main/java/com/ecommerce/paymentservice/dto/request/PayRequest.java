package com.ecommerce.paymentservice.dto.request;

import com.ecommerce.paymentservice.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Pay with a card ({"method":"CARD","card":{...}}) or UPI ({"method":"UPI","upiId":"name@bank"}). */
@Schema(description = "How to pay. The amount is always the order's total; it can't be chosen by the client.")
public record PayRequest(

        @Schema(example = "CARD")
        @NotNull(message = "method is required (CARD or UPI)")
        PaymentMethod method,

        @Valid
        CardRequest card,

        @Schema(example = "omesh@okhdfc", description = "For UPI. Ids starting with 'fail' are declined by the simulated gateway.")
        @Pattern(regexp = "^[A-Za-z0-9._-]{2,64}@[A-Za-z]{2,32}$", message = "upiId must look like name@bank")
        String upiId
) {

    @Override
    public String toString() {
        return "PayRequest[method=" + method + "]";
    }
}

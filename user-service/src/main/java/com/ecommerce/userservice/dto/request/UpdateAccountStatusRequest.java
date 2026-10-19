package com.ecommerce.userservice.dto.request;

import com.ecommerce.userservice.entity.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Change a user's account status (admin only)")
public record UpdateAccountStatusRequest(

        @Schema(example = "SUSPENDED")
        @NotNull(message = "Status is required")
        AccountStatus status
) {
}

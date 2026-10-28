package com.ecommerce.shippingservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * app.shipping.* (config-repo/shipping-service.properties).
 *
 * @param originPostalCode the warehouse's PIN code; delivery times are counted from here
 * @param timeZone         the warehouse's time zone: "today" and delivery dates are in this zone
 * @param deliveryDays     days to deliver, by how close the destination PIN code is to the warehouse's
 * @param carriers         carriers we ship with (a shipment's carrier must be one of these)
 */
@Validated
@ConfigurationProperties(prefix = "app.shipping")
public record ShippingProperties(
        @NotBlank @Pattern(regexp = "^[1-9][0-9]{5}$", message = "must be a 6-digit PIN code") String originPostalCode,
        @NotBlank String timeZone,
        @NotNull @Valid DeliveryDays deliveryDays,
        @NotEmpty List<String> carriers
) {

    public ShippingProperties {
        if (timeZone != null) {
            try {
                ZoneId.of(timeZone);
            } catch (DateTimeException ex) {
                throw new IllegalArgumentException("app.shipping.time-zone '" + timeZone + "' is not a time zone", ex);
            }
        }
        carriers = carriers == null ? List.of() : carriers.stream().map(String::trim).filter(c -> !c.isEmpty()).toList();
    }

    /**
     * PIN codes: 1st digit = zone, first 2 = region (postal circle), first 3 = sorting district (roughly a city).
     */
    public record DeliveryDays(@Positive int sameCity, @Positive int sameRegion, @Positive int sameZone, @Positive int other) {
    }

    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }

    /** The configured spelling of a carrier, matched ignoring case ("blue dart" -> "Blue Dart"). */
    public Optional<String> carrier(String name) {
        return name == null ? Optional.empty()
                : carriers.stream().filter(c -> c.equalsIgnoreCase(name.trim())).findFirst();
    }
}

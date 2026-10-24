package com.ecommerce.shippingservice.service;

import com.ecommerce.shippingservice.config.ShippingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * How long delivery takes from our warehouse to a PIN code. Indian PIN codes are hierarchical, so the more leading
 * digits a destination shares with the warehouse's, the closer it is:
 * <pre>
 *   first 3 digits equal  (same sorting district, roughly the same city)  -> same-city days   (1)
 *   first 2 digits equal  (same postal region)                            -> same-region days (2)
 *   first digit equal     (same postal zone)                              -> same-zone days   (3)
 *   otherwise                                                             -> other days       (5)
 * </pre>
 * Days are calendar days counted from today in the warehouse's time zone (couriers deliver every day).
 */
@Component
@RequiredArgsConstructor
public class DeliveryEstimator {

    public static final Pattern PIN_CODE = Pattern.compile("^[1-9][0-9]{5}$");

    private final ShippingProperties properties;
    private final Clock clock;

    public int deliveryDays(String postalCode) {
        if (postalCode == null || !PIN_CODE.matcher(postalCode).matches()) {
            throw new IllegalArgumentException("Not a 6-digit PIN code: " + postalCode);
        }
        String origin = properties.originPostalCode();
        ShippingProperties.DeliveryDays days = properties.deliveryDays();
        if (postalCode.regionMatches(0, origin, 0, 3)) {
            return days.sameCity();
        }
        if (postalCode.regionMatches(0, origin, 0, 2)) {
            return days.sameRegion();
        }
        if (postalCode.charAt(0) == origin.charAt(0)) {
            return days.sameZone();
        }
        return days.other();
    }

    /** The delivery date for a parcel leaving the warehouse at {@code from}. */
    public LocalDate estimate(String postalCode, Instant from) {
        return LocalDate.ofInstant(from, properties.zone()).plusDays(deliveryDays(postalCode));
    }

    /** The delivery date for a parcel leaving today. */
    public LocalDate estimateFromNow(String postalCode) {
        return estimate(postalCode, clock.instant());
    }
}

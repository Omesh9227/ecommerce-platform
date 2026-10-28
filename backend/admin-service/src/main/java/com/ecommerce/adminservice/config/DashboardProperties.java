package com.ecommerce.adminservice.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * app.dashboard.* (config-repo/admin-service.properties).
 *
 * @param timeZone     the shop's time zone: a "day" in reports runs midnight to midnight here
 * @param maxRangeDays longest date range a report may cover
 * @param activityDays how long activity-feed lines are kept
 */
@Validated
@ConfigurationProperties(prefix = "app.dashboard")
public record DashboardProperties(
        @NotBlank String currency,
        @NotBlank String timeZone,
        @Positive @Max(3660) int maxRangeDays,
        @Positive int activityDays
) {

    public DashboardProperties {
        if (timeZone != null) {
            try {
                ZoneId.of(timeZone);
            } catch (DateTimeException ex) {
                throw new IllegalArgumentException("app.dashboard.time-zone '" + timeZone + "' is not a time zone", ex);
            }
        }
    }

    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }
}

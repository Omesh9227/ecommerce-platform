package com.ecommerce.adminservice.report;

import com.ecommerce.adminservice.exception.BadRequestException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * A report's dates, both included, in the shop's time zone. Without dates: the last 30 days up to today.
 * start / end are the instants the period begins and ends (end exclusive: midnight after the last day).
 */
public record ReportPeriod(LocalDate from, LocalDate to, Instant start, Instant end) {

    static final int DEFAULT_DAYS = 30;

    public static ReportPeriod of(LocalDate from, LocalDate to, LocalDate today, ZoneId zone, int maxDays) {
        LocalDate last = to != null ? to : today;
        LocalDate first = from != null ? from : last.minusDays(DEFAULT_DAYS - 1L);
        if (first.isAfter(last)) {
            throw new BadRequestException("from (" + first + ") must not be after to (" + last + ")");
        }
        if (days(first, last) > maxDays) {
            throw new BadRequestException("A report can cover at most " + maxDays + " days");
        }
        return new ReportPeriod(first, last, first.atStartOfDay(zone).toInstant(), last.plusDays(1).atStartOfDay(zone).toInstant());
    }

    public long days() {
        return days(from, to);
    }

    /** The same number of days just before this period (for "compared with the previous period"). */
    public ReportPeriod previous(ZoneId zone) {
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days() - 1);
        return new ReportPeriod(prevFrom, prevTo, prevFrom.atStartOfDay(zone).toInstant(), from.atStartOfDay(zone).toInstant());
    }

    private static long days(LocalDate first, LocalDate last) {
        return ChronoUnit.DAYS.between(first, last) + 1;
    }
}

package com.ecommerce.adminservice.report;

import com.ecommerce.adminservice.exception.BadRequestException;
import com.ecommerce.adminservice.ingest.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportPeriodTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private final LocalDate today = LocalDate.of(2026, 10, 24);

    @Test
    void withoutDates_theLast30DaysUpToToday() {
        ReportPeriod p = ReportPeriod.of(null, null, today, IST, 366);
        assertThat(p.from()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(p.to()).isEqualTo(today);
        assertThat(p.days()).isEqualTo(30);
    }

    @Test
    void daysRunMidnightToMidnightInTheShopsTimeZone() {
        ReportPeriod p = ReportPeriod.of(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 24), today, IST, 366);
        assertThat(p.start()).isEqualTo(Instant.parse("2026-10-19T18:30:00Z"));
        assertThat(p.end()).isEqualTo(Instant.parse("2026-10-24T18:30:00Z"));
        ReportPeriod previous = p.previous(IST);
        assertThat(previous.from()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(previous.to()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(previous.end()).isEqualTo(p.start());
    }

    @Test
    void invalidRanges() {
        assertThatThrownBy(() -> ReportPeriod.of(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 24), today, IST, 366))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("must not be after");
        assertThatThrownBy(() -> ReportPeriod.of(LocalDate.of(2024, 1, 1), today, today, IST, 366))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("366 days");
    }

    @Test
    void moneyIsFormattedForActivityLines() {
        assertThat(Money.format(new BigDecimal("1800"), "INR")).isEqualTo("INR 1,800.00");
        assertThat(Money.format(null, "INR")).isEqualTo("-");
    }
}

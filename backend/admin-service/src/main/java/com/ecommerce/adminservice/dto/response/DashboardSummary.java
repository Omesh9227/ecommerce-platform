package com.ecommerce.adminservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The dashboard's headline numbers for a period, plus the work waiting right now.
 * Sales: gross = paid orders' totals (by payment date); refunds by refund date; net = gross - refunds.
 */
public record DashboardSummary(
        LocalDate from,
        LocalDate to,
        @Schema(example = "INR") String currency,
        Sales sales,
        Orders orders,
        Payments payments,
        Customers customers,
        Workload workload
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Sales(
            @Schema(example = "4400.00") BigDecimal grossSales,
            @Schema(example = "500.00") BigDecimal refunds,
            @Schema(example = "3900.00") BigDecimal netSales,
            @Schema(example = "4") long paidOrders,
            @Schema(example = "1100.00") BigDecimal averageOrderValue,
            @Schema(description = "Units in paid orders that weren't cancelled", example = "5") long unitsSold,
            @Schema(description = "Net sales of the same number of days just before") BigDecimal previousPeriodNetSales,
            @Schema(description = "Change against the previous period; absent if it had no sales", example = "12.5") BigDecimal netSalesChangePercent
    ) {
    }

    public record Orders(long placed, long paid, long cancelledBeforePayment, long cancelledAfterPayment, long shipped,
                         long delivered) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Payments(long succeeded, long failed,
                           @Schema(description = "Succeeded / all attempts; absent without attempts", example = "66.7")
                           BigDecimal successRatePercent,
                           long refunds) {
    }

    /** @param newBuyers customers whose first ever paid order is in the period */
    public record Customers(long buyers, long newBuyers) {
    }

    /** Right now, whatever the period. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Workload(
            @Schema(description = "Paid, not shipped yet") long ordersToShip,
            @Schema(description = "When the oldest of them was paid") Instant oldestToShipPaidAt,
            long ordersInTransit,
            long lowStockProducts,
            long deliveryFailuresLast7Days
    ) {
    }
}

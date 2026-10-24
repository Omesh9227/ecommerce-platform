package com.ecommerce.adminservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One day of sales (every day of the period is listed, also those without sales). */
public record DailySales(LocalDate date, long paidOrders, BigDecimal grossSales, BigDecimal refunds, BigDecimal netSales,
                         long unitsSold) {
}

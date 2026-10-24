package com.ecommerce.adminservice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A week of a small shop (times UTC; the shop is in India, UTC+5:30). "Now" is 24 Oct, 17:30 in India.
 * <pre>
 * O1 (U1) 1,800  Phone x2   placed 20 Oct, a declined card, then paid; shipped 21; delivered 22
 * O2 (U2)   500  Case       paid 22 Oct; cancelled 23 Oct and refunded
 * O3 (U1)   900  Phone      paid 23 Oct 18:45 UTC = 24 Oct 00:15 in India; waiting to be shipped
 * O4 (U3)   300  Cable      placed 24 Oct, card declined, cancelled unpaid
 * O5 (U3) 1,200  Charger x2 paid 24 Oct; shipped; a missed delivery
 * O6 (U4)   250  Mug        placed 24 Oct, waiting for payment
 * Stock: Charger 0 left and Phone 3 left (low, threshold 5); Case 50 left
 * </pre>
 */
class DashboardIT extends AbstractIntegrationTest {

    private final UUID phone = UUID.randomUUID();
    private final UUID charger = UUID.randomUUID();
    private Order o1;
    private Order o3;

    @BeforeEach
    void week() {
        clock.set(Instant.parse("2026-10-24T12:00:00Z"));
        UUID u1 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();
        o1 = new Order(u1, "1800", item(phone, "Phone", 2, "900"));
        placed(o1, "2026-10-20T10:00:00Z");
        paymentFailed(o1, "2026-10-20T10:02:00Z");
        paid(o1, "2026-10-20T10:05:00Z");
        shipped(o1, "2026-10-21T08:00:00Z");
        delivered(o1, "2026-10-22T09:00:00Z");

        Order o2 = new Order(UUID.randomUUID(), "500", item(UUID.randomUUID(), "Case", 1, "500"));
        placed(o2, "2026-10-22T06:00:00Z");
        paid(o2, "2026-10-22T06:05:00Z");
        cancelled(o2, "2026-10-23T09:00:00Z", "Changed my mind");
        refunded(o2, "2026-10-23T10:00:00Z");

        o3 = new Order(u1, "900", item(phone, "Phone", 1, "900"));
        placed(o3, "2026-10-23T18:40:00Z");
        paid(o3, "2026-10-23T18:45:00Z");

        Order o4 = new Order(u3, "300", item(UUID.randomUUID(), "Cable", 1, "300"));
        placed(o4, "2026-10-24T05:00:00Z");
        paymentFailed(o4, "2026-10-24T05:10:00Z");
        cancelled(o4, "2026-10-24T05:31:00Z", "Payment was not received in time");

        Order o5 = new Order(u3, "1200", item(charger, "Charger", 2, "600"));
        placed(o5, "2026-10-24T06:00:00Z");
        paid(o5, "2026-10-24T06:05:00Z");
        shipped(o5, "2026-10-24T07:00:00Z");
        recorder.apply(missedDelivery(o5, "2026-10-24T11:00:00Z"));

        placed(new Order(UUID.randomUUID(), "250", item(UUID.randomUUID(), "Mug", 1, "250")), "2026-10-24T11:30:00Z");

        recorder.apply(stock(phone, "Phone", 3, 5, "INVENTORY_LOW", 4, "2026-10-24T09:00:00Z"));
        recorder.apply(stock(charger, "Charger", 0, 5, "INVENTORY_LOW", 6, "2026-10-24T09:01:00Z"));
        recorder.apply(stock(UUID.randomUUID(), "Case", 50, 5, "INVENTORY_UPDATED", 2, "2026-10-24T09:02:00Z"));
    }

    @Test
    void summaryOfTheWeek() throws Exception {
        JsonNode s = data("/summary?from=2026-10-20&to=2026-10-24");
        assertThat(s.get("currency").asText()).isEqualTo("INR");
        JsonNode sales = s.get("sales");
        assertThat(sales.get("grossSales").decimalValue()).isEqualByComparingTo("4400.00");
        assertThat(sales.get("refunds").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(sales.get("netSales").decimalValue()).isEqualByComparingTo("3900.00");
        assertThat(sales.get("paidOrders").asLong()).isEqualTo(4);
        assertThat(sales.get("averageOrderValue").decimalValue()).isEqualByComparingTo("1100.00");
        assertThat(sales.get("unitsSold").asLong()).isEqualTo(5);  // the cancelled Case doesn't count
        assertThat(sales.has("netSalesChangePercent")).isFalse(); // nothing sold the week before

        JsonNode orders = s.get("orders");
        assertThat(orders.get("placed").asLong()).isEqualTo(6);
        assertThat(orders.get("paid").asLong()).isEqualTo(4);
        assertThat(orders.get("cancelledBeforePayment").asLong()).isEqualTo(1);
        assertThat(orders.get("cancelledAfterPayment").asLong()).isEqualTo(1);
        assertThat(orders.get("shipped").asLong()).isEqualTo(2);
        assertThat(orders.get("delivered").asLong()).isEqualTo(1);

        JsonNode payments = s.get("payments");
        assertThat(payments.get("succeeded").asLong()).isEqualTo(4);
        assertThat(payments.get("failed").asLong()).isEqualTo(2);
        assertThat(payments.get("successRatePercent").decimalValue()).isEqualByComparingTo("66.7");
        assertThat(payments.get("refunds").asLong()).isEqualTo(1);

        assertThat(s.at("/customers/buyers").asLong()).isEqualTo(3);
        assertThat(s.at("/customers/newBuyers").asLong()).isEqualTo(3);

        JsonNode work = s.get("workload");
        assertThat(work.get("ordersToShip").asLong()).isEqualTo(1);
        assertThat(work.get("oldestToShipPaidAt").asText()).isEqualTo("2026-10-23T18:45:00Z");
        assertThat(work.get("ordersInTransit").asLong()).isEqualTo(1);
        assertThat(work.get("lowStockProducts").asLong()).isEqualTo(2);
        assertThat(work.get("deliveryFailuresLast7Days").asLong()).isEqualTo(1);
    }

    @Test
    void daysAreTheShopsDays_andPeriodsAreCompared() throws Exception {
        // O3 was paid at 18:45 UTC on 23 Oct - already 24 Oct in India
        assertThat(data("/summary?from=2026-10-23&to=2026-10-23").at("/sales/paidOrders").asLong()).isZero();
        assertThat(data("/summary?from=2026-10-24&to=2026-10-24").at("/sales/paidOrders").asLong()).isEqualTo(2);
        JsonNode s = data("/summary?from=2026-10-23&to=2026-10-24");
        assertThat(s.at("/sales/grossSales").decimalValue()).isEqualByComparingTo("2100.00");
        assertThat(s.at("/sales/netSales").decimalValue()).isEqualByComparingTo("1600.00");      // minus O2's refund on 23 Oct
        assertThat(s.at("/sales/previousPeriodNetSales").decimalValue()).isEqualByComparingTo("500.00"); // 21-22 Oct: O2
        assertThat(s.at("/sales/netSalesChangePercent").decimalValue()).isEqualByComparingTo("220.0");
        assertThat(s.at("/customers/buyers").asLong()).isEqualTo(2);
        assertThat(s.at("/customers/newBuyers").asLong()).isEqualTo(1); // U1 had bought before; U3 is new

        // Without dates: the last 30 days up to today (in India)
        JsonNode month = data("/summary");
        assertThat(month.get("to").asText()).isEqualTo("2026-10-24");
        assertThat(month.get("from").asText()).isEqualTo("2026-09-25");
        assertThat(month.at("/sales/paidOrders").asLong()).isEqualTo(4);
    }

    @Test
    void salesPerDay_alsoAsCsv() throws Exception {
        JsonNode days = data("/sales/daily?from=2026-10-20&to=2026-10-24");
        List<String> rows = new ArrayList<>();
        days.forEach(d -> rows.add(d.get("date").asText() + " " + d.get("paidOrders").asLong() + " "
                + d.get("grossSales").decimalValue().stripTrailingZeros().toPlainString() + " "
                + d.get("netSales").decimalValue().stripTrailingZeros().toPlainString() + " " + d.get("unitsSold").asLong()));
        assertThat(rows).containsExactly("2026-10-20 1 1800 1800 2", "2026-10-21 0 0 0 0", "2026-10-22 1 500 500 0",
                "2026-10-23 0 0 -500 0", "2026-10-24 2 2100 2100 3");

        String csv = getAs(admin, "/sales/daily.csv?from=2026-10-20&to=2026-10-24").andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"sales-2026-10-20-to-2026-10-24.csv\""))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn().getResponse().getContentAsString();
        assertThat(csv.split("\n")).hasSize(6)
                .startsWith("date,paid_orders,gross_sales,refunds,net_sales,units_sold,currency")
                .contains("2026-10-23,0,0.00,500.00,-500.00,0,INR");
    }

    @Test
    void bestSellers() throws Exception {
        JsonNode top = data("/products/top?from=2026-10-20&to=2026-10-24");
        assertThat(top).hasSize(2); // the cancelled Case isn't a sale
        assertThat(top.at("/0/name").asText()).isEqualTo("Phone");
        assertThat(top.at("/0/unitsSold").asLong()).isEqualTo(3);
        assertThat(top.at("/0/revenue").decimalValue()).isEqualByComparingTo("2700");
        assertThat(top.at("/0/orders").asLong()).isEqualTo(2);
        assertThat(top.at("/1/name").asText()).isEqualTo("Charger");
        assertThat(data("/products/top?from=2026-10-20&to=2026-10-24&by=units&limit=1")).hasSize(1);
    }

    @Test
    void pipelineLowStockAndActivity() throws Exception {
        JsonNode pipeline = data("/orders/pipeline");
        List<String> stages = new ArrayList<>();
        pipeline.forEach(p -> stages.add(p.get("status").asText() + "=" + p.get("orders").asLong()));
        assertThat(stages).containsExactly("PENDING_PAYMENT=1", "CONFIRMED=1", "SHIPPED=1", "DELIVERED=1", "CANCELLED=2");
        assertThat(pipeline.at("/1/oldestSince").asText()).isEqualTo("2026-10-23T18:45:00Z");
        assertThat(pipeline.at("/3/oldestSince").isMissingNode()).isTrue();

        JsonNode low = data("/inventory/low-stock");
        assertThat(low).hasSize(2);
        assertThat(low.at("/0/name").asText()).isEqualTo("Charger");
        assertThat(low.at("/0/outOfStock").asBoolean()).isTrue();
        assertThat(low.at("/1/available").asInt()).isEqualTo(3);

        JsonNode feed = data("/activity?size=50");
        assertThat(feed.get("totalElements").asLong()).isEqualTo(15);
        assertThat(feed.at("/content/0/kind").asText()).isEqualTo("DELIVERY_FAILED");
        assertThat(feed.at("/content/0/message").asText()).isEqualTo("Delivery of " + feed.at("/content/0/orderNumber").asText()
                + " failed (Delhivery): Customer not available");
        JsonNode warnings = data("/activity?severity=warning");
        assertThat(warnings.get("totalElements").asLong()).isEqualTo(6);
        List<String> kinds = new ArrayList<>();
        warnings.get("content").forEach(a -> kinds.add(a.get("kind").asText()));
        assertThat(kinds).containsExactlyInAnyOrder("DELIVERY_FAILED", "LOW_STOCK", "LOW_STOCK", "PAYMENT_FAILED", "PAYMENT_FAILED",
                "PAID_ORDER_CANCELLED");
        assertThat(warnings.toString()).contains("cancelled: Changed my mind (INR 500.00 to refund)",
                "Low stock: Charger (CHARGER) - 0 left, threshold 5");
    }

    @Test
    void repeatedEventsChangeNothing() throws Exception {
        long activityBefore = count("SELECT COUNT(*) FROM activity");
        // order-service re-sends an old step; payment-service re-sends a stored event
        recorder.apply(new com.ecommerce.adminservice.event.OrderEvent(UUID.randomUUID(), "ORDER_CONFIRMED",
                Instant.parse("2026-10-20T10:05:00Z"), o1.id, o1.number, o1.userId, "CONFIRMED", null, o1.total, "INR",
                o1.items, 2));
        o3.version--; // same version as the last applied event
        recorder.apply(o3.event("ORDER_CONFIRMED", "CONFIRMED", "2026-10-23T18:45:00Z", null));
        JsonNode s = data("/summary?from=2026-10-20&to=2026-10-24");
        assertThat(s.at("/sales/paidOrders").asLong()).isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM activity")).isEqualTo(activityBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE order_id = ?", String.class, o1.id)).isEqualTo("DELIVERED");
    }

    @Test
    void onlyAdmins_andValidParameters() throws Exception {
        getAs(null, "/summary").andExpect(status().isUnauthorized());
        getAs("Bearer " + com.ecommerce.adminservice.support.TestTokens.customer(UUID.randomUUID()), "/summary")
                .andExpect(status().isForbidden());
        getAs(admin, "/summary?from=2026-10-25&to=2026-10-24").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("must not be after")));
        getAs(admin, "/summary?from=2025-01-01&to=2026-10-24").andExpect(status().isBadRequest());
        getAs(admin, "/summary?from=2026-13-01").andExpect(status().isBadRequest());
        getAs(admin, "/products/top?by=price").andExpect(status().isBadRequest());
        getAs(admin, "/products/top?limit=0").andExpect(status().isBadRequest());
        getAs(admin, "/activity?severity=LOUD").andExpect(status().isBadRequest());
        getAs(admin, "/inventory/low-stock?limit=101").andExpect(status().isBadRequest());
    }
}

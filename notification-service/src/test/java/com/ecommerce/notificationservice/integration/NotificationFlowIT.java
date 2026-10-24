package com.ecommerce.notificationservice.integration;

import com.ecommerce.notificationservice.event.EventTypes;
import com.ecommerce.notificationservice.event.OrderEvent;
import com.ecommerce.notificationservice.support.RecordingMailSender;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.MailSendException;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationFlowIT extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private void sendOnKafka(String topic, UUID key, Object event) throws Exception {
        kafkaTemplate.send(topic, key.toString(), json(event)).get(10, TimeUnit.SECONDS);
    }

    @Test
    void orderPlacedOnKafka_emailsTheCustomer() throws Exception {
        UUID orderId = UUID.randomUUID();

        sendOnKafka("order-events", orderId, orderEvent(EventTypes.ORDER_PLACED, orderId, "ORD-000000000001", 1));

        await().atMost(Duration.ofSeconds(20)).until(() -> mail.sent().size() == 1);
        MimeMessage email = mail.sent().get(0);
        assertThat(((InternetAddress) email.getAllRecipients()[0]).getAddress()).isEqualTo(EMAIL);
        assertThat(((InternetAddress) email.getFrom()[0]).getAddress()).isEqualTo("no-reply@ecommerce.local");
        assertThat(email.getSubject()).isEqualTo("Order ORD-000000000001 placed - please complete your payment");
        String body = RecordingMailSender.allText(email);
        assertThat(body).contains("<html", "Pixel 8", "INR 1,800.00", "within 30 minutes")
                .contains("2 x Pixel 8"); // plain-text part too

        await().atMost(Duration.ofSeconds(5)).until(() -> notificationCount() == 1);
        mockMvc.perform(get(NOTIFICATIONS_URL).header(HttpHeaders.AUTHORIZATION, customerAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].eventType").value("ORDER_PLACED"))
                .andExpect(jsonPath("$.data.content[0].orderNumber").value("ORD-000000000001"))
                .andExpect(jsonPath("$.data.content[0].recipient").value(EMAIL));
    }

    @Test
    void theSameEventTwice_sendsOneEmail_andConfirmedSendsNone() {
        UUID orderId = UUID.randomUUID();
        OrderEvent placed = orderEvent(EventTypes.ORDER_PLACED, orderId, "ORD-000000000002", 1);

        notificationService.onOrderEvent(placed);
        notificationService.onOrderEvent(placed);
        notificationService.onOrderEvent(orderEvent(EventTypes.ORDER_CONFIRMED, orderId, "ORD-000000000002", 2));

        assertThat(mail.sent()).hasSize(1);
        assertThat(notificationCount()).isEqualTo(1);
    }

    @Test
    void wholeJourney_oneEmailPerStep() throws Exception {
        UUID orderId = UUID.randomUUID();
        String number = "ORD-000000000003";
        notificationService.onOrderEvent(orderEvent(EventTypes.ORDER_PLACED, orderId, number, 1));
        notificationService.onPaymentEvent(paymentEvent(EventTypes.PAYMENT_FAILED, orderId, number));
        notificationService.onPaymentEvent(paymentEvent(EventTypes.PAYMENT_SUCCEEDED, orderId, number));
        notificationService.onOrderEvent(orderEvent(EventTypes.ORDER_CONFIRMED, orderId, number, 2));
        notificationService.onOrderEvent(orderEvent(EventTypes.ORDER_SHIPPED, orderId, number, 3));
        notificationService.onOrderEvent(orderEvent(EventTypes.ORDER_DELIVERED, orderId, number, 4));

        assertThat(mail.sent()).extracting(m -> subject(m)).containsExactly(
                "Order ORD-000000000003 placed - please complete your payment",
                "Payment for order ORD-000000000003 didn't go through",
                "Payment received for order ORD-000000000003",
                "Your order ORD-000000000003 has shipped",
                "Your order ORD-000000000003 was delivered");
        assertThat(RecordingMailSender.allText(mail.sent().get(2))).contains("VISA ending 4242", "Pixel 8");
        assertThat(RecordingMailSender.allText(mail.sent().get(3))).contains("Delhivery", "DLV123");
    }

    @Test
    void cancelAndRefund_emails() {
        UUID orderId = UUID.randomUUID();
        String number = "ORD-000000000004";
        notificationService.onOrderEvent(orderEvent(EventTypes.ORDER_PLACED, orderId, number, 1));
        notificationService.onOrderEvent(orderEvent(EventTypes.ORDER_CANCELLED, orderId, number, 3));
        notificationService.onPaymentEvent(paymentEvent(EventTypes.PAYMENT_REFUNDED, orderId, number));

        assertThat(mail.sent()).extracting(m -> subject(m)).containsExactly(
                "Order ORD-000000000004 placed - please complete your payment",
                "Your order ORD-000000000004 was cancelled",
                "Refund of INR 1,800.00 for order ORD-000000000004");
        assertThat(RecordingMailSender.allText(mail.sent().get(1))).contains("Changed my mind");
    }

    @Test
    void paymentEventBeforeItsOrder_isRetriedUntilTheOrderIsKnown() throws Exception {
        UUID orderId = UUID.randomUUID();
        String number = "ORD-000000000005";

        sendOnKafka("payment-events", orderId, paymentEvent(EventTypes.PAYMENT_SUCCEEDED, orderId, number));
        Thread.sleep(1500); // the payment email can't be sent yet: no order
        assertThat(mail.sent()).isEmpty();
        sendOnKafka("order-events", orderId, orderEvent(EventTypes.ORDER_PLACED, orderId, number, 1));

        await().atMost(Duration.ofSeconds(25)).until(() -> mail.sent().size() == 2);
        assertThat(mail.sent()).extracting(m -> subject(m))
                .contains("Payment received for order ORD-000000000005");
    }

    @Test
    void mailServerDown_nothingIsRecorded_andKafkaRetriesUntilItWorks() throws Exception {
        UUID orderId = UUID.randomUUID();
        mail.setFailing(true);
        assertThatThrownBy(() -> notificationService.onOrderEvent(orderEvent(EventTypes.ORDER_PLACED, orderId, "ORD-000000000006", 1)))
                .isInstanceOf(MailSendException.class);
        assertThat(notificationCount()).isZero(); // rolled back, so the retry will send it

        UUID otherOrder = UUID.randomUUID();
        sendOnKafka("order-events", otherOrder, orderEvent(EventTypes.ORDER_PLACED, otherOrder, "ORD-000000000007", 1));
        Thread.sleep(1500);
        mail.setFailing(false);

        await().atMost(Duration.ofSeconds(25)).until(() -> mail.sent().size() == 1);
        assertThat(subject(mail.sent().get(0))).contains("ORD-000000000007");
    }

    @Test
    void noEmailAddress_meansNoEmail() {
        UUID orderId = UUID.randomUUID();
        OrderEvent withoutEmail = new OrderEvent(UUID.randomUUID(), EventTypes.ORDER_PLACED, orderId, "ORD-000000000008",
                customerId, null, "X", null, null, null, null, new java.math.BigDecimal("10.00"), "INR", java.util.List.of(), 1);

        notificationService.onOrderEvent(withoutEmail);

        assertThat(mail.sent()).isEmpty();
        assertThat(notificationCount()).isZero();
    }

    @Test
    void wishlistPriceDropOnKafka_emailsTheCustomer() throws Exception {
        sendOnKafka("wishlist-events", customerId, priceDrop("1800.00", "1500.00"));

        await().atMost(Duration.ofSeconds(20)).until(() -> mail.sent().size() == 1);
        MimeMessage email = mail.sent().get(0);
        assertThat(((InternetAddress) email.getAllRecipients()[0]).getAddress()).isEqualTo(EMAIL);
        assertThat(email.getSubject()).isEqualTo("Price drop: Pixel 8 is now INR 1,500.00");
        assertThat(RecordingMailSender.allText(email))
                .contains("Pixel 8 dropped from INR 1,800.00 to INR 1,500.00", "You save", "INR 300.00");

        await().atMost(Duration.ofSeconds(5)).until(() -> notificationCount() == 1);
        mockMvc.perform(get(NOTIFICATIONS_URL).header(HttpHeaders.AUTHORIZATION, customerAuth()))
                .andExpect(jsonPath("$.data.content[0].eventType").value("WISHLIST_PRICE_DROPPED"))
                .andExpect(jsonPath("$.data.content[0].orderNumber").doesNotExist());
    }

    @Test
    void outForDeliveryOnKafka_emailsArrivingToday() throws Exception {
        UUID orderId = UUID.randomUUID();
        sendOnKafka("shipment-events", orderId, shipmentEvent(EventTypes.SHIPMENT_OUT_FOR_DELIVERY, orderId, "ORD-000000000009", null));

        await().atMost(Duration.ofSeconds(20)).until(() -> mail.sent().size() == 1);
        MimeMessage email = mail.sent().get(0);
        assertThat(email.getSubject()).isEqualTo("Your order ORD-000000000009 is out for delivery");
        assertThat(RecordingMailSender.allText(email)).contains("Arriving today", "EC482019375610");
        await().atMost(Duration.ofSeconds(5)).until(() -> notificationCount() == 1);
        mockMvc.perform(get(NOTIFICATIONS_URL).header(HttpHeaders.AUTHORIZATION, customerAuth()))
                .andExpect(jsonPath("$.data.content[0].eventType").value("SHIPMENT_OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.data.content[0].orderNumber").value("ORD-000000000009"));
    }

    @Test
    void missedDelivery_emailsOnce_andShippedFromShippingServiceSendsNothing() {
        UUID orderId = UUID.randomUUID();
        var missed = shipmentEvent(EventTypes.SHIPMENT_DELIVERY_FAILED, orderId, "ORD-000000000010", "Customer not available");

        notificationService.onShipmentEvent(missed);
        notificationService.onShipmentEvent(missed);
        notificationService.onShipmentEvent(shipmentEvent("SHIPMENT_SHIPPED", orderId, "ORD-000000000010", null));

        assertThat(mail.sent()).hasSize(1);
        assertThat(notificationCount()).isEqualTo(1);
    }

    @Test
    void theSamePriceDropTwice_sendsOneEmail() {
        var drop = priceDrop("1800.00", "1500.00");

        notificationService.onWishlistEvent(drop);
        notificationService.onWishlistEvent(drop);

        assertThat(mail.sent()).hasSize(1);
        assertThat(notificationCount()).isEqualTo(1);
    }

    @Test
    void customers_seeOnlyTheirOwnNotifications() throws Exception {
        UUID orderId = UUID.randomUUID();
        notificationService.onOrderEvent(orderEvent(EventTypes.ORDER_PLACED, orderId, "ORD-000000000009", 1));

        mockMvc.perform(get(NOTIFICATIONS_URL).header(HttpHeaders.AUTHORIZATION, otherCustomerAuth()))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get(NOTIFICATIONS_URL + "/admin").header(HttpHeaders.AUTHORIZATION, customerAuth()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(NOTIFICATIONS_URL + "/admin").param("orderNumber", "ORD-000000000009")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get(NOTIFICATIONS_URL)).andExpect(status().isUnauthorized());
    }

    private static String subject(MimeMessage message) {
        try {
            return message.getSubject();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

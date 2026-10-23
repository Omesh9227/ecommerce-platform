package com.ecommerce.notificationservice.mail;

import com.ecommerce.notificationservice.config.NotificationProperties;
import com.ecommerce.notificationservice.event.EventTypes;
import com.ecommerce.notificationservice.event.OrderEvent;
import com.ecommerce.notificationservice.event.PaymentEvent;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmailComposerTest {

    private final NotificationProperties properties = new NotificationProperties("no-reply@ecommerce.local",
            "E-Commerce Store", "E-Commerce Store", "support@ecommerce.local", 30);
    private final EmailComposer composer = new EmailComposer(properties);

    private final List<OrderEvent.Item> items = List.of(
            new OrderEvent.Item(UUID.randomUUID(), "PHONE", "Pixel 8 <Pro>", 2, new BigDecimal("900.00")),
            new OrderEvent.Item(UUID.randomUUID(), "CASE", null, 1, new BigDecimal("199.50")));

    private OrderEvent order(String type, String carrier, String tracking, String reason) {
        return new OrderEvent(UUID.randomUUID(), type, UUID.randomUUID(), "ORD-ABC123456789", UUID.randomUUID(),
                "omesh@example.com", "X", reason, carrier, tracking, new BigDecimal("1999.50"), "INR", items, 1);
    }

    private PaymentEvent payment(String type, String reason) {
        return new PaymentEvent(UUID.randomUUID(), type, UUID.randomUUID(), "ORD-ABC123456789", UUID.randomUUID(),
                new BigDecimal("1999.50"), "INR", "CARD", "VISA", "4242", reason);
    }

    @Test
    void orderPlaced_listsItemsAndAsksForPayment() {
        EmailContent email = composer.forOrderEvent(order(EventTypes.ORDER_PLACED, null, null, null)).orElseThrow();

        assertThat(email.subject()).isEqualTo("Order ORD-ABC123456789 placed - please complete your payment");
        assertThat(email.intro()).contains("within 30 minutes");
        assertThat(email.items()).extracting(EmailContent.ItemLine::name).containsExactly("Pixel 8 <Pro>", "CASE");
        assertThat(email.items().get(0).lineTotal()).isEqualTo("INR 1,800.00");
        assertThat(email.total()).isEqualTo("INR 1,999.50");
    }

    @Test
    void shipped_showsCarrierAndTracking_cancelledShowsTheReason() {
        EmailContent shipped = composer.forOrderEvent(order(EventTypes.ORDER_SHIPPED, "Delhivery", "DLV123", null)).orElseThrow();
        assertThat(shipped.details()).extracting(EmailContent.Detail::value).contains("Delhivery", "DLV123");

        EmailContent cancelled = composer.forOrderEvent(order(EventTypes.ORDER_CANCELLED, null, null, "Payment was not received in time")).orElseThrow();
        assertThat(cancelled.intro()).isEqualTo("Your order has been cancelled: Payment was not received in time.");
    }

    @Test
    void someEvents_sendNoEmail() {
        assertThat(composer.forOrderEvent(order(EventTypes.ORDER_CONFIRMED, null, null, null))).isEmpty();
        assertThat(composer.forOrderEvent(order("ORDER_SOMETHING_NEW", null, null, null))).isEmpty();
        assertThat(EmailComposer.emailsFor(payment("PAYMENT_CANCELLED", null))).isFalse();
    }

    @Test
    void paymentEmails() {
        EmailContent paid = composer.forPaymentEvent(payment(EventTypes.PAYMENT_SUCCEEDED, null), items).orElseThrow();
        assertThat(paid.intro()).isEqualTo("We received INR 1,999.50 by VISA ending 4242. We'll let you know when your order ships.");
        assertThat(paid.items()).hasSize(2);

        EmailContent failed = composer.forPaymentEvent(payment(EventTypes.PAYMENT_FAILED, "Your card was declined."), items).orElseThrow();
        assertThat(failed.intro()).isEqualTo("Your card was declined. You have not been charged.");

        EmailContent refunded = composer.forPaymentEvent(payment(EventTypes.PAYMENT_REFUNDED, null), items).orElseThrow();
        assertThat(refunded.subject()).isEqualTo("Refund of INR 1,999.50 for order ORD-ABC123456789");
    }

    @Test
    void template_rendersEveryPart_andEscapesHtml() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        engine.setTemplateResolver(resolver);
        EmailSender sender = new EmailSender(null, engine, properties);

        EmailContent email = composer.forOrderEvent(order(EventTypes.ORDER_SHIPPED, "Delhivery", "DLV123", null)).orElseThrow();
        String html = sender.html(email);

        assertThat(html).contains("Your order is on its way", "Delhivery", "DLV123", "INR 1,800.00", "INR 1,999.50",
                "x 2", "support@ecommerce.local", "E-Commerce Store");
        assertThat(html).contains("Pixel 8 &lt;Pro&gt;").doesNotContain("Pixel 8 <Pro>"); // product names are escaped

        String text = sender.plainText(email);
        assertThat(text).contains("Carrier: Delhivery", "2 x Pixel 8 <Pro>  INR 1,800.00", "Total: INR 1,999.50");
    }
}

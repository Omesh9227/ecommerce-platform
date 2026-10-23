package com.ecommerce.notificationservice.integration;

import com.ecommerce.notificationservice.event.OrderEvent;
import com.ecommerce.notificationservice.event.PaymentEvent;
import com.ecommerce.notificationservice.service.NotificationService;
import com.ecommerce.notificationservice.support.RecordingMailSender;
import com.ecommerce.notificationservice.support.TestTokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Full application context + real PostgreSQL + in-memory Kafka + a mail sender that records instead of sending. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"order-events", "payment-events"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Import(AbstractIntegrationTest.MailConfig.class)
public abstract class AbstractIntegrationTest {

    @TestConfiguration
    static class MailConfig {
        /** Replaces Spring Boot's SMTP sender (it backs off when a JavaMailSender bean exists). */
        @Bean
        JavaMailSender javaMailSender() {
            return new RecordingMailSender();
        }
    }

    protected static final String NOTIFICATIONS_URL = "/api/v1/notifications";
    protected static final String EMAIL = "omesh@example.com";

    protected final UUID customerId = UUID.randomUUID();
    protected final UUID otherCustomerId = UUID.randomUUID();

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected NotificationService notificationService;
    @Autowired
    private JavaMailSender javaMailSender;

    protected RecordingMailSender mail;

    @BeforeEach
    void cleanState() {
        jdbcTemplate.execute("TRUNCATE TABLE notifications, order_snapshots");
        mail = (RecordingMailSender) javaMailSender;
        mail.clear();
    }

    protected String customerAuth() {
        return "Bearer " + TestTokens.customer(customerId);
    }

    protected String otherCustomerAuth() {
        return "Bearer " + TestTokens.customer(otherCustomerId);
    }

    protected String adminAuth() {
        return "Bearer " + TestTokens.admin(UUID.randomUUID());
    }

    protected OrderEvent orderEvent(String type, UUID orderId, String orderNumber, long version) {
        return new OrderEvent(UUID.randomUUID(), type, orderId, orderNumber, customerId, EMAIL, "X",
                type.equals("ORDER_CANCELLED") ? "Changed my mind" : null,
                type.equals("ORDER_SHIPPED") ? "Delhivery" : null, type.equals("ORDER_SHIPPED") ? "DLV123" : null,
                new BigDecimal("1800.00"), "INR",
                List.of(new OrderEvent.Item(UUID.randomUUID(), "PHONE", "Pixel 8", 2, new BigDecimal("900.00"))), version);
    }

    protected PaymentEvent paymentEvent(String type, UUID orderId, String orderNumber) {
        return new PaymentEvent(UUID.randomUUID(), type, orderId, orderNumber, customerId, new BigDecimal("1800.00"),
                "INR", "CARD", "VISA", "4242", type.equals("PAYMENT_FAILED") ? "Your card was declined." : null);
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected long notificationCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM notifications", Long.class);
    }
}

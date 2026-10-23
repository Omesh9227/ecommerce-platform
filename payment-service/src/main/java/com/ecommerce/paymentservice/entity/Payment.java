package com.ecommerce.paymentservice.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The payment for one order. Created from ORDER_PLACED; the amount always comes from the order. */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "order_number", nullable = false, updatable = false, length = 20)
    private String orderNumber;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 10)
    private PaymentMethod method;

    @Column(name = "card_brand", length = 20)
    private String cardBrand;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "upi_id_masked", length = 100)
    private String upiIdMasked;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "refund_reference", length = 100)
    private String refundReference;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<PaymentAttempt> attempts = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static Payment pending(UUID orderId, String orderNumber, UUID userId, BigDecimal amount, String currency) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.orderNumber = orderNumber;
        payment.userId = userId;
        payment.amount = amount;
        payment.currency = currency;
        payment.status = PaymentStatus.PENDING;
        return payment;
    }

    public void addAttempt(PaymentAttempt attempt) {
        attempt.attachTo(this);
        attempts.add(attempt);
    }

    /** PENDING -> SUCCEEDED, remembering how it was paid (brand + last 4 / masked UPI id only). */
    public void markSucceeded(PaymentAttempt attempt, Instant now) {
        requireStatus(PaymentStatus.PENDING);
        status = PaymentStatus.SUCCEEDED;
        method = attempt.getMethod();
        cardBrand = attempt.getCardBrand();
        cardLast4 = attempt.getCardLast4();
        upiIdMasked = attempt.getUpiIdMasked();
        providerReference = attempt.getProviderReference();
        paidAt = now;
    }

    /** PENDING -> CANCELLED (no money was taken). */
    public void markCancelled(Instant now) {
        requireStatus(PaymentStatus.PENDING);
        status = PaymentStatus.CANCELLED;
        cancelledAt = now;
    }

    /** SUCCEEDED -> REFUNDED (the money went back). */
    public void markRefunded(String refundReference, String reason, Instant now) {
        requireStatus(PaymentStatus.SUCCEEDED);
        status = PaymentStatus.REFUNDED;
        this.refundReference = refundReference;
        this.refundReason = reason;
        refundedAt = now;
    }

    public java.util.Optional<PaymentAttempt> attemptWithKey(String idempotencyKey) {
        return attempts.stream().filter(a -> a.getIdempotencyKey().equals(idempotencyKey)).findFirst();
    }

    private void requireStatus(PaymentStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Payment for " + orderNumber + " is " + status + ", expected " + expected);
        }
    }
}

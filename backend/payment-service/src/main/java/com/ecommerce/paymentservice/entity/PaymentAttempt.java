package com.ecommerce.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One try to pay. Holds no secret card data: brand and last 4 digits only. */
@Entity
@Table(name = "payment_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, updatable = false, length = 10)
    private PaymentMethod method;

    @Column(name = "card_brand", updatable = false, length = 20)
    private String cardBrand;

    @Column(name = "card_last4", updatable = false, length = 4)
    private String cardLast4;

    @Column(name = "upi_id_masked", updatable = false, length = 100)
    private String upiIdMasked;

    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 20)
    private AttemptStatus status;

    @Column(name = "failure_code", updatable = false, length = 50)
    private String failureCode;

    @Column(name = "failure_reason", updatable = false)
    private String failureReason;

    @Column(name = "provider_reference", updatable = false, length = 100)
    private String providerReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @SuppressWarnings("java:S107") // a value object built in one place
    public PaymentAttempt(String idempotencyKey, PaymentMethod method, String cardBrand, String cardLast4,
                          String upiIdMasked, BigDecimal amount, AttemptStatus status, String failureCode,
                          String failureReason, String providerReference, Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.method = method;
        this.cardBrand = cardBrand;
        this.cardLast4 = cardLast4;
        this.upiIdMasked = upiIdMasked;
        this.amount = amount;
        this.status = status;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.providerReference = providerReference;
        this.createdAt = createdAt;
    }

    void attachTo(Payment payment) {
        this.payment = payment;
    }

    public boolean succeeded() {
        return status == AttemptStatus.SUCCEEDED;
    }
}

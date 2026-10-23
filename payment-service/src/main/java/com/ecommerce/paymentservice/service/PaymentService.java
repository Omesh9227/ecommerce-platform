package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.dto.request.CardRequest;
import com.ecommerce.paymentservice.dto.request.PayRequest;
import com.ecommerce.paymentservice.entity.AttemptStatus;
import com.ecommerce.paymentservice.entity.Payment;
import com.ecommerce.paymentservice.entity.PaymentAttempt;
import com.ecommerce.paymentservice.entity.PaymentMethod;
import com.ecommerce.paymentservice.event.EventTypes;
import com.ecommerce.paymentservice.exception.BadRequestException;
import com.ecommerce.paymentservice.exception.ConflictException;
import com.ecommerce.paymentservice.exception.PaymentProviderUnavailableException;
import com.ecommerce.paymentservice.exception.ResourceNotFoundException;
import com.ecommerce.paymentservice.gateway.CardSupport;
import com.ecommerce.paymentservice.gateway.PaymentGateway;
import com.ecommerce.paymentservice.gateway.PaymentGateway.ChargeRequest;
import com.ecommerce.paymentservice.gateway.PaymentGateway.ChargeResult;
import com.ecommerce.paymentservice.mapper.PaymentMapper;
import com.ecommerce.paymentservice.outbox.PaymentEventPublisher;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import com.ecommerce.paymentservice.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Paying an order. The whole attempt runs while the payment row is locked, so:
 * - two clicks on "Pay" can't charge twice (the second sees SUCCEEDED, or the same Idempotency-Key's attempt);
 * - an ORDER_CANCELLED event waits until the attempt is finished, then cancels or refunds accordingly.
 * (The simulated gateway answers instantly. With a real provider, a PROCESSING status + provider webhooks
 * would replace the lock held during the call.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,100}$");

    private final PaymentRepository paymentRepository;
    private final PaymentGateway gateway;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentMapper mapper;
    private final Clock clock;

    /** What was checked and will be stored about the payment method. */
    record CheckedMethod(PaymentMethod method, ChargeRequest.Card card, String upiId, String cardBrand,
                         String cardLast4, String upiIdMasked) {
    }

    @Transactional
    public PayResult pay(String orderNumber, AuthenticatedUser caller, String idempotencyKey, PayRequest request) {
        String key = validateKey(idempotencyKey);
        CheckedMethod method = checkMethod(request);

        Payment payment = paymentRepository.findByOrderNumberForUpdate(orderNumber)
                .filter(p -> p.getUserId().equals(caller.id()))
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for order " + orderNumber
                        + ". If you have just placed it, please try again in a few seconds."));

        Optional<PaymentAttempt> previous = payment.attemptWithKey(key);
        if (previous.isPresent()) {
            return result(payment, previous.get()); // same request again: same answer, nothing charged twice
        }

        switch (payment.getStatus()) {
            case SUCCEEDED -> throw new ConflictException("Order " + orderNumber + " is already paid");
            case CANCELLED -> throw new ConflictException("Order " + orderNumber + " was cancelled and can no longer be paid");
            case REFUNDED -> throw new ConflictException("Order " + orderNumber + " was refunded and can no longer be paid");
            case PENDING -> {
                // continue below
            }
        }

        ChargeResult charge;
        try {
            charge = gateway.charge(new ChargeRequest(payment.getOrderNumber(), payment.getAmount(), payment.getCurrency(),
                    method.card(), method.upiId()));
        } catch (PaymentGateway.GatewayUnavailableException ex) {
            log.warn("Payment provider unavailable for order {}: {}", orderNumber, ex.getMessage());
            throw new PaymentProviderUnavailableException();
        }

        Instant now = clock.instant();
        PaymentAttempt attempt = new PaymentAttempt(key, method.method(), method.cardBrand(), method.cardLast4(),
                method.upiIdMasked(), payment.getAmount(), charge.success() ? AttemptStatus.SUCCEEDED : AttemptStatus.FAILED,
                charge.failureCode(), charge.failureMessage(), charge.providerReference(), now);
        payment.addAttempt(attempt);

        if (charge.success()) {
            payment.markSucceeded(attempt, now);
            paymentRepository.saveAndFlush(payment);
            eventPublisher.publish(EventTypes.PAYMENT_SUCCEEDED, payment, null, now);
            log.info("Payment succeeded order={} amount={} {} method={}", orderNumber, payment.getAmount(),
                    payment.getCurrency(), describe(method));
        } else {
            paymentRepository.saveAndFlush(payment);
            eventPublisher.publish(EventTypes.PAYMENT_FAILED, payment, charge.failureMessage(), now);
            log.info("Payment declined order={} code={} method={}", orderNumber, charge.failureCode(), describe(method));
        }
        return result(payment, attempt);
    }

    // ---------- Checks ----------

    static String validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("The Idempotency-Key header is required (a new random value for each payment "
                    + "attempt, e.g. a UUID; reuse it only when retrying the same attempt)");
        }
        String key = idempotencyKey.trim();
        if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new BadRequestException("Idempotency-Key must be 8-100 letters, digits, '-' or '_'");
        }
        return key;
    }

    CheckedMethod checkMethod(PayRequest request) {
        if (request.method() == PaymentMethod.CARD) {
            CardRequest card = request.card();
            if (card == null) {
                throw new BadRequestException("card is required when method is CARD");
            }
            String digits = CardSupport.normalize(card.number());
            if (!CardSupport.isValidNumber(digits)) {
                throw new BadRequestException("The card number is not valid");
            }
            if (CardSupport.isExpired(card.expiryMonth(), card.expiryYear(), YearMonth.now(clock.withZone(ZoneOffset.UTC)))) {
                throw new BadRequestException("The card has expired");
            }
            return new CheckedMethod(PaymentMethod.CARD,
                    new ChargeRequest.Card(digits, card.expiryMonth(), card.expiryYear(), card.cvv(), card.holderName()),
                    null, CardSupport.brand(digits), CardSupport.last4(digits), null);
        }
        if (request.upiId() == null || request.upiId().isBlank()) {
            throw new BadRequestException("upiId is required when method is UPI");
        }
        String upiId = request.upiId().trim();
        return new CheckedMethod(PaymentMethod.UPI, null, upiId, null, null, CardSupport.maskUpi(upiId));
    }

    private PayResult result(Payment payment, PaymentAttempt attempt) {
        return new PayResult(mapper.toResponse(payment), attempt.succeeded(), attempt.getFailureReason());
    }

    private static String describe(CheckedMethod method) {
        return method.method() == PaymentMethod.CARD
                ? method.cardBrand() + " ****" + method.cardLast4()
                : "UPI " + method.upiIdMasked();
    }
}

# payment-service

Payments for orders, on **port 8086**. Database: `ecommerce_payment_db`. Talks to order-service only through Kafka.

```
order-service ── order-events ──► payment-service        ORDER_PLACED    → a PENDING payment for the order total
              ◄─ payment-events ──                        ORDER_CANCELLED → cancel (unpaid) or refund (paid)
                                                          PAYMENT_SUCCEEDED / PAYMENT_FAILED / PAYMENT_REFUNDED →
Postman ──► api-gateway :8080 ──► payment-service :8086   order-service confirms the order / notes the refund
              ├─ /api/v1/payments/**        → logged-in customer or admin
              └─ /api/v1/payments/admin/**  → ADMIN only
```

## How a payment works

1. The customer places an order (order-service). Its `ORDER_PLACED` event creates a **PENDING** payment here with
   the order's total. The amount always comes from the order, never from the client.
2. The customer pays: `POST /api/v1/payments/orders/{orderNumber}/pay` with a card or UPI id.
   - **Success** → payment `SUCCEEDED`, event `PAYMENT_SUCCEEDED` → order-service confirms the order (stock sold).
   - **Declined** → `402`, attempt recorded, event `PAYMENT_FAILED`; the payment stays `PENDING`, so the customer
     can try again until the order's payment window (30 min) ends.
3. The order is cancelled:
   - not paid yet → payment `CANCELLED` (paying it later gives `409`);
   - already paid → **full refund**, payment `REFUNDED`, event `PAYMENT_REFUNDED` → shown on the order's timeline.

## Payment gateway: simulated

No real money moves. `SimulatedPaymentGateway` behaves like Stripe's test mode:

| Pay with                     | Result |
|------------------------------|--------|
| Card `4242 4242 4242 4242` (any valid number, future expiry, any CVV) | succeeds |
| Card `4000 0000 0000 0002`   | 402 "Your card was declined." |
| Card `4000 0000 0000 9995`   | 402 "Your card has insufficient funds." |
| Card `4000 0000 0000 0119`   | 503 provider error (nothing charged) |
| UPI `anything@bank`          | succeeds |
| UPI `fail@bank` (starts with "fail") | 402 declined |

A real provider (Razorpay / Stripe) is a new implementation of the `PaymentGateway` interface, selected with
`app.payment.gateway` (in `config-repo/payment-service.properties`); nothing else changes.

## APIs

| Method | Path                                          | Notes |
|--------|-----------------------------------------------|-------|
| POST   | `/api/v1/payments/orders/{orderNumber}/pay`   | Header `Idempotency-Key` (new per attempt). Body: card or UPI (below). 200 paid / 402 declined |
| GET    | `/api/v1/payments/orders/{orderNumber}`       | Status, how it was paid, every attempt. Someone else's order → 404 |
| GET    | `/api/v1/payments/admin`                      | ADMIN. All payments, `status` filter, paging |

```json
{ "method": "CARD", "card": { "number": "4242 4242 4242 4242", "expiryMonth": 12, "expiryYear": 2030, "cvv": "123", "holderName": "Omesh Kumar" } }
{ "method": "UPI", "upiId": "omesh@okhdfc" }
```

| Response | When |
|----------|------|
| 200 | Paid (or this Idempotency-Key was already used and that attempt succeeded) |
| 400 | Missing/invalid Idempotency-Key, invalid card number (Luhn), expired card, missing UPI id |
| 402 | Declined - nothing charged; same key again gives the same 402 |
| 404 | No payment for this order yet (placed a second ago - retry) or not your order |
| 409 | Already paid, or the order was cancelled |
| 503 | Provider unavailable - nothing charged; retry |

## Run

1. Create the databases once:
   ```sql
   CREATE DATABASE ecommerce_payment_db;
   CREATE DATABASE ecommerce_payment_test_db;
   ```
2. Start PostgreSQL, Kafka, Redis, then all services including **payment-service** (`PaymentServiceApplication`).
3. http://localhost:8086/actuator/health, Swagger at http://localhost:8086/swagger-ui.html

Configuration from the root `.env`: `POSTGRES_PASSWORD`, `JWT_SECRET`. Optional: `PAYMENT_SERVICE_DB_NAME`,
`KAFKA_BOOTSTRAP_SERVERS`.

Watch: `kafka-console-consumer --bootstrap-server localhost:9092 --topic payment-events --from-beginning --property print.key=true`

## Test

```bash
mvn test     # unit tests
mvn verify   # + integration tests: ecommerce_payment_test_db + in-memory Kafka
```

Covered: ORDER_PLACED over Kafka, card and UPI success/decline, idempotent retries, every validation error, provider
down, ownership, cancel before/after payment (refund once even if the event comes twice), events on Kafka, and
5 simultaneous payments for one order → exactly one charge. Tests also check that no full card number is stored or
published.

## Postman

Import `payment-service/postman/payment-service.postman_collection.json` and run it with the **Collection Runner**:
it places a real order, tries invalid/declined/expired cards, pays, waits for the order to be confirmed, cancels it
and waits for the refund to reach both the payment and the order timeline.

## Design notes

- **Card data**: never stored, never logged, never published - only brand and last 4 digits (`CardRequest` and the
  gateway's `Card` even override `toString()`). With a real provider the card would be tokenised in the browser
  (Razorpay Checkout / Stripe Elements) and this service would only ever see a token.
- **No double charge**: the payment row is locked (`SELECT … FOR UPDATE`) for the whole attempt, and
  `(payment, Idempotency-Key)` is unique. A cancellation arriving at the same moment waits, then refunds.
- **Amounts come from orders**, not clients.
- **Events**: outbox → `payment-events` (key = order id). Consumer of `order-events` retries with exponential back-off
  (~30 s) before dead-lettering to `order-events.DLT`, so a short provider outage during a refund isn't lost.

# order-service

Checkout and the order lifecycle on **port 8085**. Database: `ecommerce_order_db` (owned only by this service).
Events: `order-events` on Kafka.

```
                                       ┌──► cart-service :8084       GET /api/v1/cart (customer's own token)
Postman ──► api-gateway :8080 ──► order-service :8085 ──► product-service :8082    GET /api/v1/products/lookup (current prices)
              │                        │              └──► inventory-service :8083  reservations (order-service's SERVICE token)
              │                        ├──► PostgreSQL ecommerce_order_db (orders, items, status history, outbox)
              │                        └──► Kafka order-events (ORDER_PLACED / CONFIRMED / SHIPPED / DELIVERED / CANCELLED)
              ├─ /api/v1/orders/**        → any logged-in customer or admin
              └─ /api/v1/orders/admin/**  → ADMIN only
```

## Order lifecycle

```
CREATED ──► PENDING_PAYMENT ──► CONFIRMED ──► SHIPPED ──► DELIVERED
   │              │
   ▼              ▼              ▼
CHECKOUT_FAILED  CANCELLED ◄─────┘   (customer or admin before shipping; unpaid orders also after 30 min)

PENDING_PAYMENT → CONFIRMED happens when payment-service reports PAYMENT_SUCCEEDED.
Cancelling a CONFIRMED (paid) order returns its stock and payment-service refunds it (PAYMENT_REFUNDED → timeline).
```

| Status            | Stock in inventory-service | Customer sees it |
|-------------------|----------------------------|------------------|
| `CREATED`         | being reserved (internal step, lasts milliseconds) | no |
| `PENDING_PAYMENT` | reserved                   | yes |
| `CONFIRMED`       | sold (on-hand reduced)     | yes |
| `SHIPPED` / `DELIVERED` | sold                 | yes |
| `CANCELLED`       | released (unpaid) / returned (paid, refunded) | yes |
| `CHECKOUT_FAILED` | none (e.g. not enough stock) | no |

## APIs

**Customer (and admin)**

| Method | Path                                   | Notes |
|--------|----------------------------------------|-------|
| POST   | `/api/v1/orders/checkout`              | Header `Idempotency-Key` (required). Body: `{shippingAddress, expectedTotal?}`. 201 placed / 200 same key again |
| GET    | `/api/v1/orders`                       | My orders, newest first. `status`, `page`, `size`, `sort` (createdAt, totalAmount, status) |
| GET    | `/api/v1/orders/{orderNumber}`         | With items, address and timeline. Someone else's order → 404 |
| POST   | `/api/v1/orders/{orderNumber}/cancel`  | Optional `{reason}`. Until shipped. Unpaid: stock released. Paid: stock returned + full refund |

**Admin**

| Method | Path                                              | Notes |
|--------|---------------------------------------------------|-------|
| GET    | `/api/v1/orders/admin`                            | All orders incl. internal statuses. `status`, `userId`, paging |
| POST   | `/api/v1/orders/admin/{orderNumber}/ship`         | Optional `{carrier, trackingNumber}` |
| POST   | `/api/v1/orders/admin/{orderNumber}/deliver`      | |
| POST   | `/api/v1/orders/admin/{orderNumber}/cancel`       | Same rules as the customer's cancel |

Payment: `POST /api/v1/payments/orders/{orderNumber}/pay` on payment-service (see its README). The order is
confirmed a moment later, when its `PAYMENT_SUCCEEDED` event arrives.

Every status change is idempotent: repeating it returns the order unchanged with 200.

### Checkout example

```http
POST /api/v1/orders/checkout
Authorization: Bearer <customer token>
Idempotency-Key: 3fa85f64-5717-4562-b3fc-2c963f66afa6
Content-Type: application/json

{
  "shippingAddress": { "fullName": "Omesh Kumar", "phone": "9876543210", "line1": "Flat 12B, Green Residency",
                       "line2": "MG Road", "city": "Bengaluru", "state": "Karnataka", "postalCode": "560001" },
  "expectedTotal": 1800.00
}
```

| Response | When |
|----------|------|
| 201 | Order placed: stock reserved, status PENDING_PAYMENT, cart emptied |
| 200 | This Idempotency-Key was used before: the same order is returned, nothing is done twice |
| 400 | No/invalid Idempotency-Key, invalid address, empty cart |
| 409 | Not enough stock, an item is no longer sold, or the total isn't `expectedTotal` any more |
| 503 | cart-, product- or inventory-service is down: retry with the **same** key |

Pricing: prices come from product-service at checkout (not from the cart), then never change for that order.
Shipping is ₹40, free from ₹499 (`app.order.shipping-fee`, `app.order.free-shipping-threshold`).

## Run

1. Create the databases once (pgAdmin):
   ```sql
   CREATE DATABASE ecommerce_order_db;
   CREATE DATABASE ecommerce_order_test_db;
   ```
2. Start PostgreSQL, Kafka and Redis, then user-, product-, inventory-, cart-service, **order-service**
   (`OrderServiceApplication`), and api-gateway.
3. Check http://localhost:8085/actuator/health and Swagger at http://localhost:8085/swagger-ui.html

Configuration from the root `.env`: `POSTGRES_PASSWORD`, `JWT_SECRET`. Optional: `ORDER_SERVICE_DB_NAME`,
`CART_SERVICE_URL`, `PRODUCT_SERVICE_URL`, `INVENTORY_SERVICE_URL`, `KAFKA_BOOTSTRAP_SERVERS`.

Watch order events: `kafka-console-consumer --bootstrap-server localhost:9092 --topic order-events --from-beginning --property print.key=true`

## Test

```bash
mvn test     # unit tests, nothing else needed
mvn verify   # + integration tests: ecommerce_order_test_db + in-memory Kafka + a stub for cart/product/inventory
```

Covered: checkout and its totals, idempotent retries, a double-click (6 parallel requests with one key → one order,
one reservation), every refusal (empty cart, stock, price change, discontinued product, invalid address/key),
services being down and recovering, cancel/pay/ship/deliver, access rules, events on Kafka, and the expiry job
(with a test clock).

## Postman

Import `order-service/postman/order-service.postman_collection.json` and run it with the **Collection Runner**.
It goes through the real services end to end: creates products and stock, fills the cart, checks out (also with
a wrong total and with a repeated key), cancels and sees the stock come back, then pays, ships and delivers a
second order, and finally shows that a stock drop blocks checkout.

## Design notes

- **Idempotent checkout**: the order id is derived from (user, Idempotency-Key), so a retry or a double click
  finds the same order (unique key in the database). The stock reservation uses the order number as its
  reference, and inventory-service is idempotent on it too.
- **No stock without an order**: the order row (CREATED) is saved *before* stock is reserved. If the service stops
  mid-checkout, `OrderExpiryJob` finds the CREATED order after 2 minutes and releases anything it may have reserved.
- **No network calls inside transactions** during checkout (short DB transactions in `CheckoutPersistence`).
  Later status changes lock the order row (`SELECT … FOR UPDATE`) for the inventory call, so cancel, payment and
  expiry can never both act on the same order; if inventory-service fails, nothing changes.
- **Service-to-service auth**: order-service signs its own 5-minute JWT with role `SERVICE`; inventory-service
  accepts that role only on reservation endpoints. Customers' requests to cart-service use the customer's own token.
- **Transactional outbox** for `order-events` (key = order id, so one order's events stay in order); each event
  carries `orderVersion` so consumers can ignore duplicates. Read by payment-service and notification-service, so
  events carry what emails need: the customer's email, items with names and prices, carrier/tracking once shipped.
- **Consumes `payment-events`**: `PAYMENT_SUCCEEDED` confirms the order (if it's still waiting for payment - if it was
  cancelled meanwhile, payment-service refunds on its own), `PAYMENT_REFUNDED` adds a timeline entry. Failures retry
  with exponential back-off (~30 s), then go to `payment-events.DLT`.
- **Audit trail**: `order_status_history` records every change, who made it and why; shown as the order's timeline.

# notification-service

Customer emails on **port 8087**. Database: `ecommerce_notification_db`. Listens to Kafka only; nothing calls it
to send an email.

```
order-service    ── order-events    ──►┐
payment-service  ── payment-events  ──►┤
shipping-service ── shipment-events ──►┤
wishlist-service ── wishlist-events ──►┴─► notification-service ──► SMTP (Mailpit locally) ──► customer's inbox
                                                    │
Postman ──► api-gateway :8080 ─────────────────────►┤  GET /api/v1/notifications        → the logged-in customer's emails
                                                    └  GET /api/v1/notifications/admin  → ADMIN, all emails (?orderNumber=)
```

## Emails

| Event               | Subject                                             | Contains |
|---------------------|-----------------------------------------------------|----------|
| `ORDER_PLACED`      | Order ORD-… placed - please complete your payment  | items, total, pay within 30 min |
| `PAYMENT_SUCCEEDED` | Payment received for order ORD-…                    | amount, paid with (e.g. "Visa ending 4242", "UPI") |
| `PAYMENT_FAILED`    | Payment for order ORD-… didn't go through           | reason, not charged, you can try again |
| `ORDER_SHIPPED`     | Your order ORD-… has shipped                        | carrier, tracking number, expected delivery date, items |
| `SHIPMENT_OUT_FOR_DELIVERY` | Your order ORD-… is out for delivery        | "Arriving today", carrier, tracking number (from shipping-service) |
| `SHIPMENT_DELIVERY_FAILED`  | We missed you - delivery of order ORD-…     | why it failed, attempt number, they'll try again (from shipping-service) |
| `ORDER_DELIVERED`   | Your order ORD-… was delivered                      | items, total |
| `ORDER_CANCELLED`   | Your order ORD-… was cancelled                      | reason, items |
| `PAYMENT_REFUNDED`  | Refund of INR … for order ORD-…                     | refunded amount, original payment method |
| `WISHLIST_PRICE_DROPPED` | Price drop: Pixel 8 is now INR 1,500.00       | was, now, you save (from wishlist-service) |

`ORDER_CONFIRMED` sends nothing (the payment email already says it), and neither do `SHIPMENT_SHIPPED` /
`SHIPMENT_DELIVERED` (the order's own `ORDER_SHIPPED` / `ORDER_DELIVERED` send those emails), so one step = one email. Every email is HTML (Thymeleaf template
`templates/email/notification.html`) with a plain-text version for mail apps that don't show HTML.

## APIs

| Method | Path                               | Access | Notes |
|--------|------------------------------------|--------|-------|
| GET    | `/api/v1/notifications`            | CUSTOMER, ADMIN | My emails, newest first. `page`, `size` |
| GET    | `/api/v1/notifications/admin`      | ADMIN  | All emails, `orderNumber` filter, paging |

Each item: `eventType, orderNumber, channel (EMAIL), recipient, subject, status (SENT), sentAt`.

## Run

1. Install and start **Mailpit**, a local mail server that catches every email (nothing reaches a real inbox):
   ```bash
   brew install mailpit
   brew services start mailpit      # SMTP on localhost:1025, inbox at http://localhost:8025
   ```
2. Create the databases once:
   ```sql
   CREATE DATABASE ecommerce_notification_db;
   CREATE DATABASE ecommerce_notification_test_db;
   ```
3. Start PostgreSQL, Kafka, Redis, the other services, then **notification-service**
   (`NotificationServiceApplication`) and api-gateway.
4. http://localhost:8087/actuator/health (shows DOWN if Mailpit isn't running), Swagger at http://localhost:8087/swagger-ui.html
5. Place an order and open **http://localhost:8025**: the emails are there.

Configuration from the root `.env`: `POSTGRES_PASSWORD`, `JWT_SECRET`. Optional: `NOTIFICATION_SERVICE_DB_NAME`,
`MAIL_HOST` (default `localhost`), `MAIL_PORT` (default `1025`), `KAFKA_BOOTSTRAP_SERVERS`.
Sender, store name and support address are `app.notification.*` in `backend/config-repo/notification-service.properties`.

For a real provider later (Amazon SES, SendGrid, Gmail SMTP), only `spring.mail.*` changes (host, port, username,
password, `spring.mail.properties.mail.smtp.starttls.enable=true`); the code stays the same.

## Test

```bash
mvn test     # unit tests: every email's wording, and the real template rendered (HTML escaping, plain text)
mvn verify   # + integration tests: ecommerce_notification_test_db + in-memory Kafka + a recording mail sender
```

No Mailpit needed for tests. Covered: each event over Kafka → the right email; the same event twice → one email;
a whole order journey → five emails in order; cancel and refund; a payment event arriving before its order;
mail server down → retried until it's back; no email address → nothing sent; access rules.

## Postman

Import `backend/notification-service/postman/notification-service.postman_collection.json` and run it with the
**Collection Runner** (Mailpit and all services running). It places an order, tries a declined card, pays by
UPI, ships it through shipping-service with one missed delivery, delivers it, then checks there is exactly one email
per step (8) - through the API and in Mailpit's
inbox (`http://localhost:8025/api/v1/search`).

## Design notes

- **One email per event**: `notifications.event_id` is unique, and the row is saved only after the mail server
  accepted the email. A repeated Kafka event finds the row and sends nothing.
- **Mail server down**: sending throws, the transaction rolls back, and the Kafka consumer retries with
  exponential back-off (~30 s). If it still fails, the event goes to `order-events.notification.DLT` /
  `payment-events.notification.DLT` / `wishlist-events.notification.DLT` / `shipment-events.notification.DLT` so it can be replayed; the topic is never blocked.
- **Order snapshots**: payment events don't carry the email or items, so the service keeps a small copy of each
  order (`order_snapshots`, updated only by newer `orderVersion`s). A payment event that arrives before its order
  is retried until the order is known.
- **Privacy**: addresses are masked in logs (`om***@example.com`); nothing about cards except brand and last 4.
- **Own dead-letter topics**: payment-service also consumes `order-events`; the `.notification.DLT` suffix keeps
  each consumer's failures separate.

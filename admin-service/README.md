# admin-service

The admin dashboard on **port 8093**. Database: `ecommerce_admin_db`. What a shop owner looks at every morning:
**sales** (gross, refunds, net, average order value, units, change against the previous period), **sales per day**
(also as a CSV file for a spreadsheet), **best sellers**, **orders** placed / paid / cancelled / shipped / delivered,
**payment success rate**, **buyers and new buyers**, the **work waiting right now** (orders to ship, parcels on the
way, low stock, missed deliveries) and an **activity feed** with the things that need attention marked as warnings.

```
Postman ──► api-gateway :8080 ──► admin-service :8093 ──► PostgreSQL ecommerce_admin_db
              └─ /api/v1/dashboard/** → ADMIN only

order-service     ── order-events (placed, paid, shipped, delivered, cancelled) ──►┐
payment-service   ── payment-events (succeeded, failed, refunded) ─────────────────►│
inventory-service ── inventory-events (stock, low stock) ──────────────────────────►├─► admin-service
shipping-service  ── shipment-events (missed deliveries) ──────────────────────────►┘
```

It calls no other service: reports come from its own copy of the events, so a busy dashboard never slows down
ordering, and it keeps working if another service is down.

The path is `/api/v1/dashboard` because `/api/v1/admin/**` already belongs to user-service (user management).

## APIs (all GET, ADMIN only)

| Path | Notes |
|------|-------|
| `/api/v1/dashboard/summary?from=&to=` | The headline numbers (below) |
| `/api/v1/dashboard/sales/daily?from=&to=` | One row per day, also days without sales |
| `/api/v1/dashboard/sales/daily.csv?from=&to=` | The same as a file: `sales-2026-10-01-to-2026-10-24.csv` |
| `/api/v1/dashboard/products/top?from=&to=&by=revenue\|units&limit=10` | Best sellers (paid orders that weren't cancelled) |
| `/api/v1/dashboard/orders/pipeline` | Orders per status right now; for waiting statuses, since when the oldest waits |
| `/api/v1/dashboard/inventory/low-stock?limit=50` | Products at or below their threshold, emptiest first |
| `/api/v1/dashboard/activity?severity=INFO\|WARNING&page=&size=` | Newest first |

`from` / `to` are dates (`2026-10-01`), both included. Without them: the last 30 days up to today. A report covers at
most 366 days. **Days are the shop's days** (`Asia/Kolkata`): an order paid at 18:45 UTC on 23 Oct counts for
24 Oct, as it does in India.

```json
{ "from": "2026-10-20", "to": "2026-10-24", "currency": "INR",
  "sales":     { "grossSales": 4400.00, "refunds": 500.00, "netSales": 3900.00, "paidOrders": 4,
                 "averageOrderValue": 1100.00, "unitsSold": 5, "previousPeriodNetSales": 3500.00,
                 "netSalesChangePercent": 11.4 },
  "orders":    { "placed": 6, "paid": 4, "cancelledBeforePayment": 1, "cancelledAfterPayment": 1, "shipped": 2, "delivered": 1 },
  "payments":  { "succeeded": 4, "failed": 2, "successRatePercent": 66.7, "refunds": 1 },
  "customers": { "buyers": 3, "newBuyers": 3 },
  "workload":  { "ordersToShip": 1, "oldestToShipPaidAt": "2026-10-23T18:45:00Z", "ordersInTransit": 1,
                 "lowStockProducts": 2, "deliveryFailuresLast7Days": 1 } }
```

| Number | How it's counted |
|--------|------------------|
| Gross sales, paid orders | Orders by the day they were **paid** (`ORDER_CONFIRMED`), at their total |
| Refunds | By the day the money went back (`PAYMENT_REFUNDED`) |
| Net sales | Gross - refunds. A day can be negative (a refund for an order paid earlier) |
| Average order value | Gross / paid orders |
| Units sold, best sellers | Items of paid orders that weren't cancelled, at the price they were sold for |
| Change % | Net sales against the same number of days just before; absent if that period had no sales |
| Success rate | Succeeded / all payment attempts (a declined card that's retried counts as one failure) |
| New buyers | Customers whose first paid order ever is in the period |
| Workload | Right now, whatever the period: CONFIRMED orders, SHIPPED orders, low-stock products, missed deliveries in the last 7 days |

**Activity feed** (kept 90 days):

| Kind | Severity | Example |
|------|----------|---------|
| `ORDER_PAID` | INFO | Order ORD-… paid: INR 1,800.00 |
| `ORDER_SHIPPED`, `ORDER_DELIVERED` | INFO | Order ORD-… shipped |
| `ORDER_CANCELLED` | INFO | Order ORD-… cancelled before payment: Payment was not received in time |
| `PAID_ORDER_CANCELLED` | WARNING | Paid order ORD-… cancelled: Changed my mind (INR 500.00 to refund) |
| `REFUND_COMPLETED` | INFO | Refund of INR 500.00 for ORD-… completed |
| `PAYMENT_FAILED` | WARNING | Payment of INR 1,800.00 for ORD-… failed: Your card was declined. |
| `LOW_STOCK` | WARNING | Low stock: Pixel 9 (PIX-9) - 2 left, threshold 5 |
| `DELIVERY_FAILED` | WARNING | Delivery of ORD-… failed (Delhivery): Customer not available |

| Response | When |
|----------|------|
| 400 | Not a date, `from` after `to`, more than 366 days, `by` not revenue/units, `limit` / `size` not 1–100, unknown severity |
| 401 / 403 | Not logged in / not an ADMIN |

## How the data is kept

- **Orders**: one row per order with its latest status and the time of each step (placed, paid, shipped, delivered,
  cancelled, refunded) and its items. Only a newer `orderVersion` changes the status; a step's time, once known, stays.
- **Payments** and **missed deliveries**: one row per event id. **Stock**: the newest `inventoryVersion` per product.
- **Activity**: one line per event id, written in the same transaction as the report data.

So events may arrive twice, late or out of order without counting twice. Bad events are retried with back-off, then
go to `<topic>.admin.DLT`. On its first start the service reads the four topics from the beginning, so the dashboard
already shows the orders, payments and stock from before this phase.

Settings (currency, time zone, longest report, how long the feed is kept) are in `config-repo/admin-service.properties`.

## Run

1. Create the databases once:
   ```sql
   CREATE DATABASE ecommerce_admin_db;
   CREATE DATABASE ecommerce_admin_test_db;
   ```
2. Start discovery-server, config-server, the other services, then **admin-service** (`AdminServiceApplication`),
   then api-gateway.
3. http://localhost:8093/actuator/health, Swagger at http://localhost:8093/swagger-ui.html

Configuration from the root `.env`: `POSTGRES_PASSWORD`, `JWT_SECRET`, Config Server login. Optional:
`ADMIN_SERVICE_DB_NAME`.

## Test

```bash
mvn test     # unit tests: report periods and the shop's days, money format, JWT
mvn verify   # + integration tests: ecommerce_admin_test_db + in-memory Kafka
```

Covered: a week of a small shop (six orders: paid, refunded, paid just after midnight in India, cancelled unpaid,
missed delivery, waiting for payment) and every number of the summary, comparison with the previous period, days in
the shop's time zone (fails if they were UTC days), sales per day and the CSV, best sellers without cancelled orders,
the pipeline, low stock, the activity feed and its warnings, repeated / older / unknown / incomplete events, the feed
keeping 90 days, access rules, parameter checks, and all four Kafka listeners with dead-lettering.

## Postman

Import `admin-service/postman/admin-service.postman_collection.json` and run it with the **Collection Runner**.
It reads the dashboard, then a customer buys a product, another order's card is declined and a paid order is
cancelled, and the collection waits for each to show up in the summary, the pipeline, best sellers and the feed.

## Design notes

- **Read model from events** (JDBC, no JPA): reports are a few indexed SQL queries over this service's own tables.
  Nothing is pre-aggregated, so a corrected event or a new report needs no migration of totals.
- **Money by the day it moved**: sales by payment date, refunds by refund date - the way a bank statement or an
  accountant sees it, so past days don't change when an old order is refunded.
- **Separate from the services it reports on**: heavy date-range queries never touch the order or payment databases.

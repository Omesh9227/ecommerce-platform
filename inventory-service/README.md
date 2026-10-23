# inventory-service

Stock levels and order reservations on **port 8083**. Database: `ecommerce_inventory_db` (owned only by this service).
Talks to product-service through **Kafka** only — never by HTTP.

```
                 product-events (PRODUCT_CREATED / UPDATED / DISCONTINUED)
product-service ──────────────────────────────────────────────► inventory-service
      ▲                                                               │
      └────────────── inventory-events (INVENTORY_UPDATED / LOW) ─────┘

Postman ──► api-gateway :8080 ──► inventory-service :8083 ──► PostgreSQL (ecommerce_inventory_db)
              └─ /api/v1/inventory/** → ADMIN only
```

- Creating a product in product-service creates its inventory item here automatically (0 on hand).
- Every stock change is sent back, so product-service can show `availableQuantity` / `inStock` without calling this service.

## Stock model

| Field              | Meaning                                                            |
|--------------------|--------------------------------------------------------------------|
| `quantityOnHand`   | Physically in the warehouse                                        |
| `quantityReserved` | Held for orders that are not confirmed yet                         |
| `availableQuantity`| `onHand − reserved` — what can still be sold                       |
| `lowStockThreshold`| Default 5 (`config-repo/inventory-service.properties`). `lowStock` is true when available ≤ threshold |

Reservation lifecycle:

```
reserve ──► RESERVED ──confirm──► CONFIRMED ──return──► RETURNED   (paid order cancelled: on-hand goes back up)
                    └──release──► RELEASED    (cancelled before payment: reserved goes down, stock is available again)
```

## APIs (ADMIN; the reservation endpoints also accept order-service's own token, role `SERVICE`)

| Method | Path                                                  | Notes                                                       |
|--------|-------------------------------------------------------|-------------------------------------------------------------|
| GET    | `/api/v1/inventory`                                   | `lowStock=true`, `search` (SKU/name), page/size/sort        |
| GET    | `/api/v1/inventory/{productId}`                       | 404 if the product has no inventory item                    |
| PUT    | `/api/v1/inventory/{productId}/stock`                 | `{quantityOnHand, reason}` — exact count; not below reserved |
| POST   | `/api/v1/inventory/{productId}/stock/adjustments`     | `{quantityChange, reason}` — `+` restock, `−` removal, not 0 |
| PUT    | `/api/v1/inventory/{productId}/threshold`             | `{lowStockThreshold}`                                       |
| GET    | `/api/v1/inventory/{productId}/movements`             | Audit trail, newest first                                   |
| POST   | `/api/v1/inventory/reservations`                      | `{orderReference, items:[{productId, quantity}]}` → 201; same orderReference again → 200 with the existing one |
| GET    | `/api/v1/inventory/reservations/{orderReference}`     |                                                             |
| POST   | `/api/v1/inventory/reservations/{orderReference}/release` | Idempotent; 409 if already confirmed                    |
| POST   | `/api/v1/inventory/reservations/{orderReference}/confirm` | Idempotent; 409 if already released                     |
| POST   | `/api/v1/inventory/reservations/{orderReference}/return`  | Paid order cancelled before shipping: sold units back on hand (`RETURNED`) |

Reservation errors: **409** not enough stock or product not `ACTIVE` (the message lists every problem),
**400** unknown product or invalid request. A reservation is all-or-nothing.

Sortable list fields: `sku, productName, quantityOnHand, quantityReserved, lowStockThreshold, updatedAt`.

## Run

1. Kafka must be running (see the root README: `brew services start kafka`).
2. In pgAdmin, create the databases once:
   ```sql
   CREATE DATABASE ecommerce_inventory_db;
   CREATE DATABASE ecommerce_inventory_test_db;
   ```
3. Start user-service, product-service, **inventory-service** (`InventoryServiceApplication`), then api-gateway.
4. Check http://localhost:8083/actuator/health and Swagger at http://localhost:8083/swagger-ui.html

Configuration comes from the root `.env`: `POSTGRES_PASSWORD`, `JWT_SECRET`. Optional:
`INVENTORY_SERVICE_DB_NAME` (default `ecommerce_inventory_db`), `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`).

Products created **before** inventory-service existed are picked up too: product-service's Flyway V2 queues a
`PRODUCT_CREATED` event for every existing product, and this service reads `product-events` from the beginning.

## Test

```bash
mvn test     # unit tests, no database or Kafka needed
mvn verify   # + integration tests: ecommerce_inventory_test_db + an in-memory Kafka (no local Kafka needed)
```

Integration tests cover: Kafka consumption (including out-of-order/duplicate events and the dead-letter topic),
stock APIs and security, outbox events reaching Kafka, the reservation lifecycle, and **concurrency**
(25 parallel orders for 10 units → exactly 10 succeed; the same order sent 8 times at once → reserved once).

## Postman

Import `inventory-service/postman/inventory-service.postman_collection.json` and run it with the
**Collection Runner** (right-click → Run collection). It creates two products through product-service, waits for
the Kafka events, then sets stock, reserves, confirms, releases, checks that product-service shows the new
availability, and that a discontinued product can't be reserved. The "Wait for …" requests retry for up to 20 s;
that retry only works in the Runner.

## Design notes

- **No overselling**: stock rows are locked (`SELECT … FOR UPDATE`, always in productId order so two multi-item
  orders can't deadlock) for the whole check-and-update, and a database CHECK (`reserved ≤ on hand`) is the
  last line of defence.
- **Idempotent reservations**: `orderReference` is unique, so a retried request can never reserve twice.
- **Transactional outbox**: events are saved in `outbox_events` in the same transaction as the stock change and
  sent to Kafka by a background relay. If Kafka is down, nothing is lost — events go out when it's back.
- **Safe consumers**: product events carry `productVersion`; older or repeated events are ignored. Malformed events
  go to `product-events.DLT` after retries instead of blocking the topic.
- **Low-stock alert** (`INVENTORY_LOW`) fires once when available stock drops to the threshold and re-arms
  when stock recovers — the notification-service will use it later.
- **Audit trail**: every change writes a stock movement (who, what, why, resulting quantities).

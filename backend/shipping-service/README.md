# shipping-service

Shipments for paid orders on **port 8090**. Database: `ecommerce_shipping_db`. When an order is paid, a shipment
appears in the warehouse's queue with a **delivery estimate**; the warehouse **packs** and **ships** it with a carrier
(tracking number generated if the carrier gave none); the carrier's **scans** (in transit, out for delivery, missed
delivery, delivered) build the **tracking history**. order-service follows along (SHIPPED, DELIVERED) and the
customer gets emails. Anyone with the tracking number can track the parcel; anyone can ask when an order to a PIN
code would arrive.

```
Postman ──► api-gateway :8080 ──► shipping-service :8090 ──► PostgreSQL ecommerce_shipping_db
              ├─ GET /api/v1/shipments/track/**, /delivery-estimate → public
              ├─ /api/v1/shipments/admin/**                        → ADMIN (warehouse, carrier scans)
              └─ /api/v1/shipments/**                              → logged in (your own shipments)

order-service ── order-events ──────► shipping-service      ORDER_CONFIRMED → new shipment; ORDER_CANCELLED → cancel it
shipping-service ── shipment-events ─► order-service        SHIPPED / DELIVERED (+ timeline lines for the scans in between)
                                    └► notification-service "Arriving today" and "We missed you" emails
```

shipping-service calls no other service: everything it needs (address, items, customer) arrives in `ORDER_CONFIRMED`.

## Shipment lifecycle

```
PENDING ──► PACKED ──► SHIPPED ──► IN_TRANSIT ──► OUT_FOR_DELIVERY ──► DELIVERED
   │           │                    (each hub)          │      ▲
   ▼           ▼                                        ▼      │ next attempt
CANCELLED ◄────┘  (order cancelled)              DELIVERY_FAILED
```

| Status | Where the parcel is | Announced on shipment-events |
|--------|---------------------|------------------------------|
| `PENDING` | Paid, waiting to be packed | – |
| `PACKED` | Packed, waiting for the carrier | – |
| `SHIPPED` | Handed to the carrier | `SHIPMENT_SHIPPED` → order SHIPPED, "has shipped" email |
| `IN_TRANSIT` | At a hub (one line per hub) | – |
| `OUT_FOR_DELIVERY` | With the delivery agent | `SHIPMENT_OUT_FOR_DELIVERY` → "Arriving today" email |
| `DELIVERY_FAILED` | Attempt failed (reason in `statusReason`) | `SHIPMENT_DELIVERY_FAILED` → "We missed you" email |
| `DELIVERED` | Delivered | `SHIPMENT_DELIVERED` → order DELIVERED, "delivered" email, verified purchase for reviews |
| `CANCELLED` | The order was cancelled before shipping | – (the order's own `ORDER_CANCELLED` already did it) |

Events are keyed by order id and carry `shipmentVersion`; order-service ignores anything not newer than the last one
it applied.

## APIs

**Customers and public**

| Method | Path | Access | Notes |
|--------|------|--------|-------|
| GET | `/api/v1/shipments` | logged in | My shipments, newest first. Sort: `createdAt`, `updatedAt`, `estimatedDeliveryDate`, `status` |
| GET | `/api/v1/shipments/orders/{orderNumber}` | owner or admin | Status, carrier, tracking number, estimated delivery, address, items, tracking history. 404 if not yours or not paid yet |
| GET | `/api/v1/shipments/track/{trackingNumber}` | public | Status, estimate, destination city, history. **No** names, phone, street or order number |
| GET | `/api/v1/shipments/delivery-estimate?postalCode=110001` | public | `{deliveryDays: 5, estimatedDeliveryDate, message: "Delivery by Thu, 29 Oct"}` |

**Warehouse and carrier (ADMIN)**

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/v1/shipments/admin` | `status` (e.g. `PENDING` = to pack), `orderNumber`, `carrier`; oldest first |
| GET | `/api/v1/shipments/admin/{id}` | One shipment, with the customer's `userId` |
| POST | `/api/v1/shipments/admin/{id}/pack` | PENDING → PACKED |
| POST | `/api/v1/shipments/admin/{id}/ship` | `{carrier, trackingNumber?, location?}` PACKED → SHIPPED; new estimate from today |
| POST | `/api/v1/shipments/admin/{id}/tracking` | `{status, location?, note?}`: `IN_TRANSIT` (location required), `OUT_FOR_DELIVERY`, `DELIVERY_FAILED` (note = reason, required), `DELIVERED` |

Repeating an update that already happened returns the shipment unchanged (200), e.g. a carrier sending the same scan
twice or two people pressing "ship". A move the lifecycle doesn't allow is a 409 (ship before packing, scan before
shipping, anything after delivery or cancellation).

| Response | When |
|----------|------|
| 400 | Unknown carrier (the message lists ours), bad tracking number (6–40 letters, digits, dashes), wrong status for a scan, missing location/note, not a PIN code |
| 403 | Not an admin (warehouse APIs) |
| 404 | No such shipment / tracking number; someone else's order |
| 409 | Not allowed from the current status; tracking number already used by another shipment |

## Delivery estimates

Indian PIN codes are hierarchical, so the more leading digits a destination shares with the warehouse's
(`app.shipping.origin-postal-code`, 560001 Bengaluru), the closer it is:

| Shared digits | Example from 560001 | Days (`app.shipping.delivery-days.*`) |
|---------------|---------------------|------|
| 3 (same sorting district ≈ city) | 560034 Bengaluru | 1 |
| 2 (same postal region) | 562110 Bengaluru rural | 2 |
| 1 (same zone) | 500081 Hyderabad | 3 |
| none | 110001 Delhi, 600001 Chennai | 5 |

Counted in calendar days from today in the warehouse's time zone (`Asia/Kolkata`). Set when the order is paid, and
again when the parcel ships. Settings and the list of carriers (`Delhivery, Blue Dart, Ecom Express, India Post`)
are in `backend/config-repo/shipping-service.properties`.

## Run

1. Create the databases once:
   ```sql
   CREATE DATABASE ecommerce_shipping_db;
   CREATE DATABASE ecommerce_shipping_test_db;
   ```
2. Start discovery-server, config-server, the other services, then **shipping-service**
   (`ShippingServiceApplication`), then api-gateway.
3. http://localhost:8090/actuator/health, Swagger at http://localhost:8090/swagger-ui.html

Configuration from the root `.env`: `POSTGRES_PASSWORD`, `JWT_SECRET`, Config Server login. Optional:
`SHIPPING_SERVICE_DB_NAME`.

Orders paid **before** this phase have no address in their `ORDER_CONFIRMED` event, so they get no shipment (a warning
in the log). Cancel them, or place new orders.

## Test

```bash
mvn test     # unit tests: lifecycle rules, delivery estimates, carriers
mvn verify   # + integration tests: ecommerce_shipping_test_db + in-memory Kafka (order events sent by the tests)
```

Covered: shipments from `ORDER_CONFIRMED` (address, items, estimate, repeated events, old events without an address,
malformed events going to `order-events.shipping.DLT`), cancellation in the warehouse and after dispatch, the whole
journey with a missed delivery and the events it publishes (types, order, versions), repeated updates, refused moves,
validation, duplicate tracking numbers, **6 simultaneous "ship" requests → shipped once, one event**, customers vs
admins vs public tracking (no personal details), delivery estimates and the admin queue filters.

## Postman

Import `backend/shipping-service/postman/shipping-service.postman_collection.json` and run it with the **Collection Runner**:
delivery estimates, then an order is paid, packed, shipped, scanned at two hubs, misses one delivery, is delivered
(order-service following each step), and a second order is cancelled before shipping.

## Design notes

- **No calls to other services**: the order event carries everything a shipment needs, so shipping keeps working
  when order-service is down, and an address change later never moves a paid order.
- **One shipment per order**: `order_id` is unique and a repeated event is skipped.
- **Row lock per update**: pack, ship, scans and the cancellation from order-service lock the shipment, so they never
  overlap; together with "repeat = no change" this makes double clicks and carrier retries harmless.
- **Cancel vs ship race**: if the customer cancels at the very moment the parcel is shipped, the order stays cancelled
  (and refunded), and both the order's timeline and the shipment's history say the parcel must be recalled.
- **Tracking history**: one row per step, never changed; ordered by time, then by insertion (`seq`).
- **Events**: transactional outbox → `shipment-events` (key = order id). The order-events consumer retries with
  back-off, then dead-letters to `order-events.shipping.DLT`.

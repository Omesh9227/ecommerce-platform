# cart-service

The logged-in user's shopping cart on **port 8084**. Carts live in **Redis** (no PostgreSQL database for this service);
prices and stock are read live from product-service every time the cart is shown.

```
Postman ──► api-gateway :8080 ──► cart-service :8084 ──► Redis  (cart:{userId} hash, expires after 30 days unused)
              └─ /api/v1/cart/** → any logged-in user          │
                                                              └──► product-service :8082
                                                                   GET /api/v1/products/lookup?ids=...  (one call per cart)
```

The cart always belongs to the caller in the JWT. There is no user id in any URL, so nobody can read or change
someone else's cart.

## APIs (any logged-in user — customers and admins each have their own cart)

| Method | Path                              | Body                     | Notes                                                    |
|--------|-----------------------------------|--------------------------|----------------------------------------------------------|
| GET    | `/api/v1/cart`                    |                          | Cart with live prices, stock, totals                     |
| POST   | `/api/v1/cart/items`              | `{productId, quantity}`  | Adds to the quantity if already in the cart              |
| PUT    | `/api/v1/cart/items/{productId}`  | `{quantity}`             | Sets the quantity (≥ 1; use DELETE to remove)            |
| DELETE | `/api/v1/cart/items/{productId}`  |                          | Removes it (removing twice is fine)                      |
| DELETE | `/api/v1/cart`                    |                          | Empties the cart → 204                                   |

All endpoints except DELETE /cart return the whole cart:

```json
{
  "items": [{
    "productId": "…", "sku": "PIXEL-8", "name": "Pixel 8", "imageUrl": "…", "quantity": 2,
    "unitPrice": 53999.10, "listPrice": 59999.00, "priceWhenAdded": 55999.00, "priceChanged": true,
    "lineTotal": 107998.20, "availableQuantity": 14, "status": "AVAILABLE",
    "message": "Price dropped from 55999.00 to 53999.10", "addedAt": "…"
  }],
  "totalQuantity": 2, "subtotal": 107998.20, "totalSavings": 11999.80, "currency": "INR",
  "checkoutReady": true, "pricesUpToDate": true, "warnings": []
}
```

| Item `status`        | Meaning                                                        | Counted in subtotal |
|----------------------|----------------------------------------------------------------|---------------------|
| `AVAILABLE`          | For sale, enough stock                                         | yes                 |
| `INSUFFICIENT_STOCK` | Fewer in stock than in the cart ("Only 2 left in stock")       | no                  |
| `OUT_OF_STOCK`       | No stock                                                       | no                  |
| `UNAVAILABLE`        | Not for sale any more (shown with the name from when it was added) | no              |
| `UNKNOWN`            | product-service is down; price from when it was added          | no                  |

`checkoutReady` is true only when every item is `AVAILABLE`.

### Rules

| Rule                                               | Error |
|----------------------------------------------------|-------|
| Product must exist and be ACTIVE                   | 404   |
| Product must be in stock; total can't exceed stock | 409   |
| At most 10 units per product (`app.cart.max-quantity-per-item`) | 400 |
| At most 50 different products (`app.cart.max-items`) | 400 |
| Adding needs product-service                       | 503 if it's down |
| Lowering a quantity is always allowed, even for an item that sold out | — |

Stock is **checked, not held**. It is reserved only at checkout (order-service, next phase), so a cart can never
lock stock away from other shoppers.

## Run

1. Redis must be running (see the root README: `brew services start redis`, then `redis-cli ping` → `PONG`).
2. Start user-service, product-service (and inventory-service + Kafka if you want stock to change), then
   **cart-service** (`CartServiceApplication`), then api-gateway.
3. Check http://localhost:8084/actuator/health (includes Redis) and Swagger at http://localhost:8084/swagger-ui.html

Configuration comes from the root `.env`: `JWT_SECRET`. Optional: `REDIS_HOST` (default `localhost`),
`REDIS_PORT` (default `6379`), `PRODUCT_SERVICE_URL` (default `http://localhost:8082`).

### Look inside Redis

```bash
redis-cli --scan --pattern 'cart:*'              # one key per user with a cart
redis-cli HGETALL cart:<userId>                  # field = productId, value = the stored line (JSON)
redis-cli TTL cart:<userId>                      # seconds until the cart expires (2592000 = 30 days)
```

(`--scan` is safe on a busy server; `KEYS cart:*` blocks Redis while it runs, so avoid it outside development.)

## Test

```bash
mvn test     # unit tests, no Redis needed
mvn verify   # + integration tests: needs local Redis running; uses Redis database 15 and empties it before each test
```

Your dev carts (database 0) are never touched by the tests. product-service is replaced by a small stub server, so it
doesn't need to be running. Covered: limits, stock, price changes, discontinued products, product-service down,
per-user isolation, expiry, and **25 parallel adds** against a limit of 10 (exactly 10 succeed).

## Postman

Import `cart-service/postman/cart-service.postman_collection.json` and run it with the **Collection Runner**.
It creates products, sets stock through inventory-service, then as a new customer adds, updates and removes items,
and checks that a price drop, a stock drop and a discontinued product all show up in the cart. Needs Kafka,
Redis and all services running.

## Design notes

- **Why Redis**: carts are read and changed far more often than orders, are per-user key/value data, and should
  disappear on their own when abandoned (TTL). No schema, no migrations, very fast.
- **Atomic changes**: "check the limits, then write" runs as a Lua script inside Redis
  (`src/main/resources/scripts`), so two requests from the same user can't both slip past a limit.
- **Live prices**: the cart stores only quantities plus a snapshot. Every view asks product-service for current
  prices and stock in **one** call, so the price shown is never stale and price changes are pointed out.
- **Graceful degradation**: if product-service is slow or down (2 s connect / 3 s read timeouts), the cart is still
  shown from the snapshot with `pricesUpToDate: false`; only adding new items fails (503).

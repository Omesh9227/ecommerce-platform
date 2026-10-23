# wishlist-service

Customers' wishlists on **port 8088**. Database: `ecommerce_wishlist_db`. Saved products show their **live** price
and stock, can be **moved to the cart**, and the customer gets an **email when a saved product gets cheaper**.

```
Postman ──► api-gateway :8080 ──► wishlist-service :8088 ──► PostgreSQL ecommerce_wishlist_db
              └─ /api/v1/wishlist/** → logged-in customer    ├──► product-service  lb://  GET /api/v1/products/lookup (live prices)
                                                             └──► cart-service     lb://  POST /api/v1/cart/items (customer's own token)

product-service ── product-events ──► wishlist-service ── wishlist-events ──► notification-service ──► "Price drop" email
                   (price changes)     (who saved it?)     WISHLIST_PRICE_DROPPED
```

## APIs (the caller's own wishlist - no user id in any URL)

| Method | Path | Notes |
|--------|------|-------|
| GET    | `/api/v1/wishlist` | Newest first, live price and stock, `priceDropped` / `priceDifference` since saving |
| POST   | `/api/v1/wishlist/items` | `{productId}` → 201 saved / 200 already saved. Out-of-stock products can be saved |
| DELETE | `/api/v1/wishlist/items/{productId}` | 204 (also when it wasn't saved) |
| DELETE | `/api/v1/wishlist` | Empty it → 204 |
| GET    | `/api/v1/wishlist/contains?productIds=a,b` | Which of these are saved (the "heart" on product pages), up to 100 |
| POST   | `/api/v1/wishlist/items/{productId}/move-to-cart` | Optional `{quantity}` (default 1). Added through cart-service, then removed from the wishlist |

Each item:

```json
{ "productId": "…", "sku": "PIXEL-8", "name": "Pixel 8", "imageUrl": "…",
  "price": 53999.10, "listPrice": 59999.00, "priceWhenAdded": 55999.00, "priceDifference": -1999.90,
  "priceDropped": true, "availableQuantity": 14, "status": "AVAILABLE",
  "message": "Price dropped from 55999.00 to 53999.10", "addedAt": "…" }
```

| `status` | Meaning |
|----------|---------|
| `AVAILABLE` | For sale and in stock |
| `OUT_OF_STOCK` | For sale, no stock right now - stays saved |
| `UNAVAILABLE` | Not sold any more - shown with the name and price from when it was saved |
| `UNKNOWN` | product-service is down - shown from the saved snapshot (`pricesUpToDate: false`) |

| Response | When |
|----------|------|
| 400 | Invalid request, wishlist full (100 products, `app.wishlist.max-items`), more than 100 ids |
| 404 | Product not for sale (save), or not in the wishlist (move) |
| 409 etc. | Move to cart: cart-service's own answer is passed on, e.g. "Pixel 8 is out of stock" - the product stays saved |
| 503 | product-service (save) or cart-service (move) not responding |

## Price-drop emails

product-service now sends each product's `price` and `sellingPrice` on product-events. wishlist-service:

1. Remembers each product's latest price and `productVersion` (`product_prices`), so an old or repeated event
   is ignored.
2. For every customer who saved the product, compares the new price with the price **they last saw** - when they
   saved it, or in our previous email. If it is at least **5 % lower** (`app.wishlist.price-drop-min-percent`),
   it writes `WISHLIST_PRICE_DROPPED` to the outbox and remembers that price.
3. notification-service emails: *"Price drop: Pixel 8 is now INR 1,500.00"* - was / now / you save.

So each email is about a new, lower price: no repeats when the price goes up and back down, and no email for a
1-rupee change.

## Run

1. Create the databases once:
   ```sql
   CREATE DATABASE ecommerce_wishlist_db;
   CREATE DATABASE ecommerce_wishlist_test_db;
   ```
2. Start discovery-server, config-server, the other services, then **wishlist-service**
   (`WishlistServiceApplication`), then api-gateway.
3. http://localhost:8088/actuator/health, Swagger at http://localhost:8088/swagger-ui.html

Configuration from the root `.env`: `POSTGRES_PASSWORD`, `JWT_SECRET`, Config Server login. Optional:
`WISHLIST_SERVICE_DB_NAME`. Rules in `config-repo/wishlist-service.properties`.

## Test

```bash
mvn test     # unit tests: response building, price-drop rules
mvn verify   # + integration tests: ecommerce_wishlist_test_db + in-memory Kafka + a stub for product-/cart-service
```

Covered: save / list / remove / clear / contains, live prices and price changes, out-of-stock and discontinued
products, product-service down, the limit, other customers' wishlists, **8 simultaneous saves of one product → one
row**, **10 simultaneous saves with room for 5 → exactly 5**, move to cart (token forwarded, cart refusing, cart
down), and price drops: over Kafka, threshold, no repeats, old/repeated events, inactive products, several
customers, and malformed events going to `product-events.wishlist.DLT`.

## Postman

Import `wishlist-service/postman/wishlist-service.postman_collection.json` and run it with the **Collection
Runner**: saves two products (one out of stock), puts the headphones on sale (20 % off), waits for the price-drop
email (notifications API and Mailpit), then moves them to the cart.

## Design notes

- **PostgreSQL, not Redis**: a wishlist is kept for months and is the basis for emails - unlike a cart, it must not
  expire or be lost.
- **No duplicates, no going past the limit**: changes to one customer's wishlist take a PostgreSQL advisory lock
  (keyed by user id) for their short transaction; `(user_id, product_id)` is also unique.
- **Network calls outside transactions**: product-service is asked first, then a short database transaction saves.
- **Move to cart** forwards the customer's own token, so cart-service applies its usual rules; the product leaves
  the wishlist only after the cart accepted it.
- **Events**: transactional outbox → `wishlist-events` (key = user id). The product-events consumer retries with
  back-off, then dead-letters to `product-events.wishlist.DLT` (inventory-service keeps its own `product-events.DLT`).

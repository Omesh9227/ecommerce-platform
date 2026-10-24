# product-service

Product catalog on **port 8082**: categories (as a tree), products, pricing with discounts, search and filters.
Database: `ecommerce_product_db` (owned only by this service).

```
Browser / Postman ──► api-gateway :8080 ──► product-service :8082 ──► PostgreSQL (ecommerce_product_db)
                        │
                        ├─ GET /api/v1/products/**, /api/v1/categories/**   → public
                        └─ POST / PUT / DELETE on those paths              → ADMIN only

product-service ── product-events ──► Kafka ──► inventory-service, wishlist-service (price drops), search-service
product-service ── category-events ─► Kafka ──► search-service (category names and tree)
product-service ◄── inventory-events ── Kafka ◄── inventory-service   (availableQuantity / inStock)
product-service ◄── review-events ──── Kafka ◄── review-service      (averageRating / reviewCount)
```

## APIs

| Method | Path                         | Access | Notes                                                       |
|--------|------------------------------|--------|-------------------------------------------------------------|
| GET    | `/api/v1/categories`         | public | Full tree, nested, sorted by name                           |
| GET    | `/api/v1/categories/{id}`    | public | Category + parent + direct children                         |
| POST   | `/api/v1/categories`         | ADMIN  | `{name, description, parentId}`; omit parentId for top level |
| PUT    | `/api/v1/categories/{id}`    | ADMIN  | Rename / move (not under its own sub-category)              |
| DELETE | `/api/v1/categories/{id}`    | ADMIN  | Only if no sub-categories and no products                   |
| GET    | `/api/v1/products`           | public | Search, filter, sort, paginate (below)                      |
| GET    | `/api/v1/products/{id}`      | public | Customers get 404 for non-ACTIVE products                   |
| POST   | `/api/v1/products`           | ADMIN  | SKU unique (stored upper-case)                              |
| PUT    | `/api/v1/products/{id}`      | ADMIN  | Replaces all fields; status kept if omitted                 |
| DELETE | `/api/v1/products/{id}`      | ADMIN  | Soft delete → status `DISCONTINUED`                         |
| GET    | `/api/v1/products/lookup?ids=a,b,c` | public | Up to 50 ids in one call; returns only the ACTIVE ones (used by cart-service) |
| POST   | `/api/v1/products/events/republish` | ADMIN | Sends every category and product again on Kafka (current versions), e.g. to fill search-service. Safe to repeat |

### Search and filters (`GET /api/v1/products`)

| Param        | Example                     | Meaning                                              |
|--------------|-----------------------------|------------------------------------------------------|
| `search`     | `iphone`                    | Case-insensitive match in name, brand or SKU         |
| `categoryId` | `uuid`                      | That category **and all its sub-categories**         |
| `brand`      | `Apple`                     | Exact brand, case-insensitive                        |
| `minPrice`   | `10000`                     | Selling price ≥ (after discount)                     |
| `maxPrice`   | `80000`                     | Selling price ≤                                      |
| `status`     | `INACTIVE`                  | ADMIN only; customers always get `ACTIVE`            |
| `inStock`    | `true`                      | Only products with stock available (`false` = sold out) |
| `page/size`  | `page=0&size=20`            | Max size 100                                         |
| `sort`       | `sellingPrice,asc`          | `name, brand, price, sellingPrice, availableQuantity, averageRating, reviewCount, createdAt, updatedAt` |

### Pricing

- `price` is the list price (MRP); `discountPercent` is 0 to below 100.
- `sellingPrice = price × (100 − discount) / 100`, rounded half-up to 2 decimals, calculated by the service
  (clients never send it). Example: 999.00 at 15% → 849.15.
- All prices are in INR (`app.catalog.currency` in `config-repo/product-service.properties`, from `shop.currency`).

### Product status

| Status         | Customers see it | Meaning                                  |
|----------------|------------------|------------------------------------------|
| `ACTIVE`       | yes              | Normal                                   |
| `INACTIVE`     | no (404)         | Temporarily hidden                       |
| `DISCONTINUED` | no (404)         | Deleted (soft) — kept for future orders |

### Events (Kafka)

| Topic              | Direction | Events                                                     |
|--------------------|-----------|------------------------------------------------------------|
| `product-events`   | publishes | `PRODUCT_CREATED`, `PRODUCT_UPDATED`, `PRODUCT_DISCONTINUED` (key = productId), with `price`, `sellingPrice` and (since Phase 12) `brand`, `description`, `categoryId`, `discountPercent`, `imageUrls`, `createdAt` |
| `category-events`  | publishes | `CATEGORY_CREATED`, `CATEGORY_UPDATED` (rename / move), `CATEGORY_DELETED` (key = categoryId), with `name`, `parentId`, `categoryVersion` |
| `inventory-events` | consumes  | `INVENTORY_UPDATED` → sets `availableQuantity` / `inStock` on the product |
| `review-events`    | consumes  | `PRODUCT_RATING_UPDATED` → sets `averageRating` / `reviewCount` (only if `ratingVersion` is newer) |

Products start with `availableQuantity: 0`, `inStock: false` until inventory-service reports stock, and with
`averageRating: 0.0`, `reviewCount: 0` until review-service reports a rating. Best rated first:
`GET /api/v1/products?sort=averageRating,desc`. Ratings that can't be applied go to `review-events.DLT`.

## Run

0. Kafka must be running (see the root README: `brew services start kafka`).
1. In pgAdmin, create the databases once:
   ```sql
   CREATE DATABASE ecommerce_product_db;
   CREATE DATABASE ecommerce_product_test_db;
   ```
2. Start **user-service** (8081) so you can log in, then **product-service** (8082), then **api-gateway** (8080).
3. Check http://localhost:8082/actuator/health and Swagger at http://localhost:8082/swagger-ui.html

Configuration comes from the root `.env`: `POSTGRES_PASSWORD`, `JWT_SECRET` (same as user-service). Optional:
`PRODUCT_SERVICE_DB_NAME` (default `ecommerce_product_db`), `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`).

If Kafka is down, product changes still work: events wait in the `outbox_events` table and are sent when Kafka is back.

## Test

```bash
mvn test     # unit tests, no database needed
mvn verify   # + integration tests against ecommerce_product_test_db (wiped before each test)
             #   and an in-memory Kafka started by the tests (no local Kafka needed)
```

## Postman

Import `product-service/postman/product-service.postman_collection.json` and run it top to bottom.
It goes through the gateway (8080), logs in as admin and a new customer, builds
Electronics → Mobiles / Laptops, creates products and checks filters, pricing, visibility, security and
soft delete. Every run uses a unique tag, so it can be re-run any time.

## Design notes

- **Security**: the gateway enforces public-read / admin-write, and this service enforces it again with its
  own JWT validation (defence in depth). There is no user table here; the role comes from the verified token.
- **No N+1 queries**: category and image URLs are loaded in batches (`default_batch_fetch_size`); the
  category tree is built in memory from one query.
- **Search escaping**: `%` and `_` in search text are matched literally.
- **Concurrency**: duplicate SKUs / category names are also blocked by unique indexes; `version` columns give
  optimistic locking.
- **Transactional outbox**: a product change and its event are saved in one transaction (`outbox_events`), and a
  background relay sends events to Kafka — no event is lost if Kafka is down, and none is sent for a rolled-back change.
- **Availability is a read copy**: `availableQuantity` is only written by the `INVENTORY_UPDATED` consumer, which
  ignores events older than the one it already applied (`inventoryVersion`).
- Image upload comes later (URLs for now).

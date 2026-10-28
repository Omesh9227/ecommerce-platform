# review-service

Product reviews and star ratings on **port 8089**. Database: `ecommerce_review_db`. Customers rate a product 1–5
stars with a title and comment; reviews from people who **received** the product carry a **verified purchase**
badge; other customers vote reviews **helpful**; admins can **hide** a review. Every change updates the product's
rating summary (e.g. *4.3 from 120 reviews*), which product-service shows on the product and can sort by.

```
Postman ──► api-gateway :8080 ──► review-service :8089 ──► PostgreSQL ecommerce_review_db
              ├─ GET /api/v1/reviews/products/** → public  ├──► product-service lb://  GET /api/v1/products/lookup (is it for sale?)
              ├─ /api/v1/reviews/admin/**        → ADMIN    └──► user-service    lb://  GET /api/v1/users/me (reviewer's name)
              └─ /api/v1/reviews/**              → logged in

order-service ── order-events ──► review-service                    (ORDER_DELIVERED → who received which product)
review-service ── review-events ──► product-service                 (PRODUCT_RATING_UPDATED → averageRating, reviewCount)
```

## APIs

| Method | Path | Access | Notes |
|--------|------|--------|-------|
| GET    | `/api/v1/reviews/products/{productId}` | public | Visible reviews. `rating=5`, `verifiedOnly=true`, `page`, `size`, `sort` (`createdAt`, `rating`, `helpfulCount`; default newest first) |
| GET    | `/api/v1/reviews/products/{productId}/summary` | public | `averageRating`, `reviewCount`, `ratingCounts` (5 stars first) |
| POST   | `/api/v1/reviews/products/{productId}` | customer | `{rating, title, comment}` → 201. One review per customer per product |
| GET    | `/api/v1/reviews/me` | customer | My reviews, including hidden ones with the reason |
| PUT    | `/api/v1/reviews/{reviewId}` | author | Change rating/title/comment; sets `editedAt` |
| DELETE | `/api/v1/reviews/{reviewId}` | author or admin | 204 |
| POST   | `/api/v1/reviews/{reviewId}/helpful` | customer | Vote helpful (once; not your own review) |
| DELETE | `/api/v1/reviews/{reviewId}/helpful` | customer | Take the vote back |
| GET    | `/api/v1/reviews/admin` | ADMIN | All reviews, `status=HIDDEN`, `productId=…` |
| POST   | `/api/v1/reviews/admin/{reviewId}/hide` | ADMIN | `{reason}` - hidden from the public and from the rating |
| POST   | `/api/v1/reviews/admin/{reviewId}/unhide` | ADMIN | Show it again |

A review as the public sees it:

```json
{ "id": "…", "productId": "…", "reviewerName": "Omesh K.", "rating": 5, "title": "Love it",
  "comment": "Fast and the camera is great.", "verifiedPurchase": true, "helpfulCount": 12,
  "status": "VISIBLE", "createdAt": "…", "editedAt": "…" }
```

The reviewer is shown as first name + last initial (from user-service, with the reviewer's own token); never the
email or user id. The author also sees `hiddenReason`; admins also see `userId`.

| Response | When |
|----------|------|
| 400 | Invalid request (rating not 1–5, title missing or over 150, comment over 4000), voting for your own review |
| 401 / 403 | Not logged in / not an admin (admin APIs) / buyers-only reviews switched on and you never received it |
| 404 | Product not for sale (writing), review not found or not yours (edit) |
| 409 | You already reviewed this product |
| 503 | product-service not responding (writing) |

## Verified purchases

order-service publishes `ORDER_DELIVERED` on `order-events` with the order's items. review-service records each
(customer, product) in `purchases` and marks any review that customer already wrote for it as verified. New reviews
check `purchases` when written. So a customer can review before the parcel arrives, and the badge appears once it does.

`app.review.verified-purchase-required` in `backend/config-repo/review-service.properties` (default `false`): set it to
`true` and only customers who received the product can review it (403 otherwise), as many shops do.

## Rating summary

`product_ratings` holds, per product, the count of each star rating, the average (one decimal, rounded half up)
and a `ratingVersion`. Writing, editing, deleting, hiding or un-hiding a review recounts it **in the same
transaction**, with the summary row locked, so simultaneous reviews never lose a count. The new summary goes to the
outbox and out as `PRODUCT_RATING_UPDATED` on `review-events` (key = product id). product-service stores
`averageRating` and `reviewCount` only if the version is newer, so an old or repeated event can't roll it back.

## Run

1. Create the databases once:
   ```sql
   CREATE DATABASE ecommerce_review_db;
   CREATE DATABASE ecommerce_review_test_db;
   ```
2. Start discovery-server, config-server, the other services, then **review-service**
   (`ReviewServiceApplication`), then api-gateway.
3. http://localhost:8089/actuator/health, Swagger at http://localhost:8089/swagger-ui.html

Configuration from the root `.env`: `POSTGRES_PASSWORD`, `JWT_SECRET`, Config Server login. Optional:
`REVIEW_SERVICE_DB_NAME`. Rules in `backend/config-repo/review-service.properties`.

## Test

```bash
mvn test     # unit tests: averages and rounding, reviewer names, the buyers-only rule
mvn verify   # + integration tests: ecommerce_review_test_db + in-memory Kafka + a stub for product-/user-service
```

Covered: write / read / edit / delete, filters and sorting, validation, one review per product, other customers'
reviews, public vs author vs admin views, helpful votes (twice, own review, taking it back), hiding and un-hiding,
verified purchases from `ORDER_DELIVERED` (before and after the review, repeated events, other event types),
`PRODUCT_RATING_UPDATED` events on Kafka, malformed events going to `order-events.review.DLT`,
**10 simultaneous reviews → count 10, correct average** and **6 simultaneous submits by one customer → one 201,
five 409**. product-service has its own tests for applying rating events (`ProductRatingIT`).

## Postman

Import `backend/review-service/postman/review-service.postman_collection.json` and run it with the **Collection Runner**.
Two customers review a phone; one of them buys it (checkout → pay → ship → deliver) and his review becomes a
verified purchase; helpful votes, an edit, hiding and a delete each change the rating, and the collection waits for
product-service to show the new `averageRating` / `reviewCount`.

## Design notes

- **Ratings are computed by review-service, shown by product-service**: product pages and sorting by rating need no
  call to review-service; the summary travels as an event.
- **Recount, don't increment**: each change recounts that product's visible reviews (indexed by product), so the
  summary can't drift, e.g. after a hidden review is edited.
- **No duplicate reviews**: `(product_id, user_id)` is unique; a simultaneous second submit gets 409, not 500.
- **Helpful votes**: `review_votes (review_id, user_id)` primary key, `INSERT … ON CONFLICT DO NOTHING`, and the
  count changes only when a row was actually added or removed.
- **Network calls outside transactions**: product-service and user-service are asked first, then a short
  transaction saves.
- **Events**: transactional outbox → `review-events`. The order-events consumer retries with back-off, then
  dead-letters to `order-events.review.DLT`.

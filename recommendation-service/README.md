# recommendation-service

Product recommendations on **port 8092**. Database: `ecommerce_recommendation_db`. The rows a shop shows around its
products: **Frequently bought together**, **Customers who viewed this also viewed**, **Similar products**,
**Trending**, **Recommended for you** and **Recently viewed**. Every product in a list can be bought right now
(for sale and in stock) and comes with the reason it is there ("Because you bought Pixel 9").

```
Postman ──► api-gateway :8080 ──► recommendation-service :8092 ──► PostgreSQL ecommerce_recommendation_db
              ├─ GET /api/v1/recommendations/products/**, /trending → public
              ├─ /api/v1/recommendations/for-you, /views, /recently-viewed → logged-in customer
              └─ /api/v1/recommendations/admin/**                  → ADMIN

product-service   ── product-events, category-events ──►┐
inventory-service ── inventory-events ─────────────────►│
review-service    ── review-events ────────────────────►├─► recommendation-service
order-service     ── order-events (paid / cancelled) ──►┘
the shop's product page ── POST /views ─────────────────►
```

It calls no other service: what it needs arrives as events (and product views through its API).

## APIs

| Method | Path | Access | Notes |
|--------|------|--------|-------|
| GET | `/products/{id}/bought-together` | public | In the most paid orders together with this product |
| GET | `/products/{id}/also-viewed` | public | Viewed by the most customers who viewed this one |
| GET | `/products/{id}/similar` | public | Same category first, then related ones (parent / child / sibling); similar price and same brand rank higher. 404 for an unknown product |
| GET | `/trending?categoryId=` | public | Most units sold in the last 7 days (category includes sub-categories), topped up with top-rated products |
| GET | `/for-you` | customer | Personal; see below |
| POST | `/views` | customer | `{productId}` when a product page is opened → 204 |
| GET | `/recently-viewed` | customer | Newest first |
| DELETE | `/recently-viewed` | customer | Forget my views → 204 |
| GET | `/admin/stats` | ADMIN | Products known / recommendable, paid orders, pairs bought and viewed together |

All paths start with `/api/v1/recommendations`. Every list takes `?limit=` (default 8, at most 20) and answers:

```json
{ "title": "Frequently bought together",
  "products": [ { "productId": "…", "name": "Phone Case", "brand": "Spigen", "sellingPrice": 999.00, "currency": "INR",
                  "averageRating": 3.9, "reviewCount": 10, "inStock": true, "categoryName": "Accessories",
                  "reason": "Bought together in 2 orders" } ] }
```

## Recommended for you

Starts from the customer's 10 latest purchases and views, collects signals and adds them up per product:

| Signal | Weight | Reason shown |
|--------|--------|--------------|
| bought together with something you bought | 4.5 – 9 (more orders, more weight) | Because you bought Pixel 9 |
| viewed together with something you viewed | 3.3 – 6 | Because you viewed Galaxy S24 |
| same category as something you bought or viewed | 1 – 1.5 (better rated, more) | Similar to Pixel 9 |
| selling well in those categories | 0.55 – 1.5 | Popular in Phones |

The reason shown is the product's strongest signal. What other customers actually bought or viewed together always
outranks "same category" + "popular" combined. Products you bought or recently viewed are left out (they have their
own lists). Short lists are filled with bestsellers, then top-rated products, so new customers see something useful.

## How the data is kept

- **Paid orders** (`ORDER_CONFIRMED`): recorded once per order; each product's sales for the day go up and every
  pair of products in the order counts one more "bought together". A **cancelled paid order** (`ORDER_CANCELLED`)
  takes exactly those counts back, once. Unpaid cancelled orders never counted.
- **Views**: the first time a customer views a product it counts one "viewed together" with each of their 20 latest
  views, both ways; reloading a page counts nothing. A customer's views are recorded one at a time (advisory lock),
  so pages opened at once are still paired. Each customer keeps their 200 latest products; `DELETE /recently-viewed`
  forgets them (the anonymous pair counts stay).
- **Catalog** (products, categories, stock, ratings): the same versioned copy as search-service keeps - newer data
  wins, events may arrive in any order. Only ACTIVE, in-stock products are recommended.

Bad events go to `<topic>.recommendation.DLT`. Settings are in `config-repo/recommendation-service.properties`.

On its first start the service reads the topics from the beginning, so earlier orders, products (including the
Phase 12 republish) and stock all count straight away.

## Run

1. Create the databases once:
   ```sql
   CREATE DATABASE ecommerce_recommendation_db;
   CREATE DATABASE ecommerce_recommendation_test_db;
   ```
2. Start discovery-server, config-server, the other services, then **recommendation-service**
   (`RecommendationServiceApplication`), then api-gateway.
3. http://localhost:8092/actuator/health, Swagger at http://localhost:8092/swagger-ui.html

Configuration from the root `.env`: `POSTGRES_PASSWORD`, `JWT_SECRET`, Config Server login. Optional:
`RECOMMENDATION_SERVICE_DB_NAME`.

## Test

```bash
mvn test     # unit tests: how signals are weighed and ranked
mvn verify   # + integration tests: ecommerce_recommendation_test_db + in-memory Kafka
```

Covered: every list on a small shop with real orders (ordering, reasons, limits, sold-out and other-category
products left out), a cancelled paid order taken back once, trending with a category and with old sales, recommended
for you from purchases, from views and for a new customer, recently viewed and clearing it, also viewed counting
customers not reloads, **6 product pages opened at once by one customer → all 15 pairs counted**, access rules,
repeated and older events, and the Kafka listeners with dead-lettering.

## Postman

Import `recommendation-service/postman/recommendation-service.postman_collection.json` and run it with the
**Collection Runner**: a buyer pays for two products (bought together, trending), a second customer browses (also
viewed, recently viewed), both get recommendations for them, then the order is cancelled and the counts follow.

## Design notes

- **Counters kept as events arrive**, not computed on every request: each list is an indexed lookup, and an order
  and its counts change in one transaction, so they always agree.
- **Explainable**: every product carries its reason, which also makes the behaviour easy to test.
- **Privacy**: only product ids are stored per customer (no names or emails); pair counts are anonymous; customers can
  clear their history.
- **Rules, not machine learning (yet)**: co-purchase / co-view counts and category similarity are what most shops start
  with; the API stays the same if a trained model replaces the ranking later.

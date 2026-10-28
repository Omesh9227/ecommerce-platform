# search-service

Product search on **port 8091**. Database: `ecommerce_search_db`. A search box like a big shop's: words are
matched as you type them ("pix" finds Pixel), the best matches come first, **misspellings are corrected**
("pixle" → "Showing results for pixel"), and results can be narrowed by category, brand, price, rating and
availability, with **counts next to every filter**. Also search-box suggestions and popular searches.

```
Postman ──► api-gateway :8080 ──► search-service :8091 ──► PostgreSQL ecommerce_search_db
              ├─ GET /api/v1/search/products, /suggest, /popular → public           (full-text + pg_trgm)
              └─ /api/v1/search/admin/**                       → ADMIN

product-service   ── product-events, category-events ──►┐
inventory-service ── inventory-events ─────────────────►├─► search-service (its own copy of the catalog)
review-service    ── review-events ────────────────────►┘
```

search-service calls no other service: it keeps its own searchable copy of the catalog, built only from events, so
searching never puts load on product-service and keeps working if it is down.

## APIs (all GET, no login)

| Path | Notes |
|------|-------|
| `/api/v1/search/products` | `q`, `categoryId` (with sub-categories), `brand` (repeat for several), `minPrice`, `maxPrice`, `minRating` (1–5), `inStock`, `sort`, `page`, `size` (≤ 50), `facets` (default true) |
| `/api/v1/search/suggest?q=pix` | Matching products, brands, categories and popular searches starting with the text. Empty `q`: popular searches |
| `/api/v1/search/popular?limit=10` | Most searched words recently (only searches that found something; anonymous) |
| `/api/v1/search/admin/stats` | ADMIN: products indexed / searchable / still missing details, categories, dictionary size |

`sort`: `relevance` (default), `price_asc`, `price_desc`, `rating`, `newest`.

```json
{ "query": "pixle 9", "correctedQuery": "pixel 9",
  "results": { "content": [ { "productId": "…", "name": "Pixel 9", "brand": "Google", "sellingPrice": 71999.10,
                              "price": 79999.00, "discountPercent": 10.00, "currency": "INR", "averageRating": 4.5,
                              "reviewCount": 120, "inStock": true, "availableQuantity": 5, "categoryName": "Phones", … } ],
               "page": 0, "size": 20, "totalElements": 2, … },
  "facets": { "brands": [ { "value": "Google", "count": 2 } ],
              "categories": [ { "id": "…", "name": "Phones", "count": 2 } ],
              "priceRanges": [ { "label": "Under INR 500", "min": 0, "max": 500, "count": 0 }, …,
                               { "label": "INR 20,000 & above", "min": 20000, "count": 2 } ],
              "ratings": [ { "minRating": 4, "label": "4 stars & up", "count": 1 }, … ],
              "availability": { "inStock": 1, "outOfStock": 1 } } }
```

## How it finds things

- **Full-text search** (PostgreSQL `tsvector`, GIN index): every word must match, and may be the start of a longer
  word. English stemming, so "phones" finds "Phone". Name, brand and SKU weigh most, then the category name, then
  the description; an exact or starts-with name match gets an extra boost. Products in stock come first.
- **Typos**: if a query finds nothing, each word that isn't in the dictionary (all words of product names, brands
  and categories) gets the closest dictionary word: candidates by trigram similarity (`pg_trgm`), winner by the
  fewest typing mistakes (1 for short words, 2 for longer; swapped neighbouring letters count as one). Numbers are
  never changed. The response says which query the results are for (`correctedQuery`).
- **Facets** count each filter's options with all the other filters applied but not its own, so after choosing
  "Google" the other brands are still offered with their counts.
- **Popular searches**: a count per normalized search (no user ids), for the last 30 days.

Settings (price ranges, page size, suggestions, popular-search window) are in `backend/config-repo/search-service.properties`.

## Keeping the copy in step

| Event | Updates |
|-------|---------|
| `PRODUCT_CREATED` / `UPDATED` / `DISCONTINUED` | name, brand, description, category, prices, image, status (only ACTIVE products are found) |
| `CATEGORY_CREATED` / `UPDATED` / `DELETED` | the category tree (for "with sub-categories") and the category name on its products |
| `INVENTORY_UPDATED` / `INVENTORY_LOW` | stock |
| `PRODUCT_RATING_UPDATED` | average rating and number of reviews |

Each source has its own version column and only newer data is applied, so events may arrive late, twice or in any
order (stock before the product, a category after its products). Product and category updates for the same category
take a short PostgreSQL advisory lock, so the two topics can't race. Bad events go to `<topic>.search.DLT`.

**Products that existed before search-service**: their old events don't carry brand, category or description.
After the first start, call product-service's `POST /api/v1/products/events/republish` (ADMIN) once: it sends the
whole catalog again with the current versions, and search fills in the rest. `GET /api/v1/search/admin/stats`
shows `withoutDetails: 0` when it's done. (The Postman collection does this as its first step.)

## Run

1. Create the databases once:
   ```sql
   CREATE DATABASE ecommerce_search_db;
   CREATE DATABASE ecommerce_search_test_db;
   ```
   Flyway creates the `pg_trgm` extension (part of standard PostgreSQL, including Homebrew's).
2. Start discovery-server, config-server, the other services, then **search-service**
   (`SearchServiceApplication`), then api-gateway.
3. http://localhost:8091/actuator/health, Swagger at http://localhost:8091/swagger-ui.html

Configuration from the root `.env`: `POSTGRES_PASSWORD`, `JWT_SECRET`, Config Server login. Optional:
`SEARCH_SERVICE_DB_NAME`.

## Test

```bash
mvn test     # unit tests: query words, edit distance, sort names, settings
mvn verify   # + integration tests: ecommerce_search_test_db + in-memory Kafka
```

Covered: prefixes, ranking (name over description, exact name first, in stock first), stemming, brand and SKU
matches, every filter alone and combined, facet counts, every sort, paging, typo correction (and when not to correct),
suggestions, popular searches (counting rules and time window), public vs admin access, validation, events out of
order / repeated / older, stock and ratings before the product, discontinued products, categories arriving late,
renamed, moved and deleted, **a category and its product arriving at the same moment (25 times)**, the Kafka
listeners and dead-lettering.

## Postman

Import `backend/search-service/postman/search-service.postman_collection.json` and run it with the **Collection Runner**.
It republishes the catalog, creates four products, searches them every way, then changes stock, adds a review,
renames a category and discontinues a product, waiting for search to follow each change.

## Design notes

- **PostgreSQL, not Elasticsearch (yet)**: full-text search, trigram indexes and facet counts in the database we
  already run, with the same tests everywhere. The search API doesn't expose the engine, so moving to Elasticsearch
  or OpenSearch later (e.g. in the Docker phase) changes only this service.
- **Read model**: one wide row per product with a generated, weighted `tsvector` column; queries are hand-written SQL
  (JDBC, no JPA) with all input as bind parameters - only letters and digits ever reach a `tsquery`.
- **A failing popular-search counter never fails a search.**

# E-Commerce Platform (Amazon-like) — Spring Boot Microservices

Built incrementally, phase by phase. Every service runs and is tested **locally first**;
Docker/Docker Compose is added only after all services work locally.

## Services

| Service         | Port | Database               | Status             |
|-----------------|------|------------------------|--------------------|
| discovery-server | 8761 | — (Eureka registry)   | Phase 8 — complete |
| config-server   | 8888 | — (serves `config-repo/`) | Phase 8 — complete |
| api-gateway     | 8080 | —                      | Phase 1 — complete |
| user-service    | 8081 | `ecommerce_user_db`    | Phase 1 — complete |
| product-service | 8082 | `ecommerce_product_db` | Phase 2 — complete; Kafka events in Phase 3 |
| inventory-service | 8083 | `ecommerce_inventory_db` | Phase 3 — complete |
| cart-service    | 8084 | Redis (`cart:{userId}`) | Phase 4 — complete |
| order-service   | 8085 | `ecommerce_order_db`   | Phase 5 — complete; payments in Phase 6 |
| payment-service | 8086 | `ecommerce_payment_db` | Phase 6 — complete (simulated gateway) |
| notification-service | 8087 | `ecommerce_notification_db` | Phase 7 — complete (emails via Mailpit locally) |
| wishlist-service | 8088 | `ecommerce_wishlist_db` | Phase 9 — complete (live prices, move to cart, price-drop emails) |
| review-service  | 8089 | `ecommerce_review_db`  | Phase 10 — complete (ratings, verified purchases, helpful votes, moderation) |
| shipping-service | 8090 | `ecommerce_shipping_db` | Phase 11 — complete (packing, carriers, tracking, delivery estimates) |
| search-service  | 8091 | `ecommerce_search_db`  | Phase 12 — complete (full-text search, typo correction, filters with counts, suggestions) |
| recommendation-service | 8092 | `ecommerce_recommendation_db` | Phase 13 — complete (bought together, also viewed, similar, trending, for you) |
| admin-service   | 8093 | `ecommerce_admin_db`   | Phase 14 — complete (dashboard: sales, orders, payments, stock, deliveries, activity feed) |

All client traffic goes through the gateway on **8080** (see `api-gateway/README.md`).
Start order: PostgreSQL + Kafka + Redis + Mailpit → **discovery-server → config-server** → user-service → product-service → inventory-service → cart-service → order-service → payment-service → notification-service → wishlist-service → review-service → shipping-service → search-service → recommendation-service → admin-service → api-gateway.
Each service has its own README with APIs, run/test steps and a Postman collection.

Still to come before Docker: Prometheus + Grafana (metrics and monitoring dashboards).
Docker, Kubernetes, Jenkins and GCP follow once every service runs locally.

## Service discovery and configuration (Phase 8)

```
                 discovery-server :8761 (Eureka)        config-server :8888 ──reads── config-repo/*.properties
                  ▲ register / look up                    │ settings at startup
                  │                                       ▼
Postman ──► api-gateway :8080 ── lb://order-service ──► order-service ── lb://inventory-service ──► ...
```

- **Eureka** (`discovery-server`): every service registers itself; the gateway, cart-service and order-service find
  each other by **name** (`lb://order-service`) instead of fixed `localhost:8085` URLs. Dashboard:
  http://localhost:8761.
- **Config Server** (`config-server`): business settings (shipping fee, payment window, cart limits, email sender...)
  live in [`config-repo/`](config-repo) - change one, restart that service, no rebuild. Technical settings stay
  in each service's `application.properties`; secrets stay in `.env`.
- Both need a login, from `.env`: `EUREKA_USERNAME/PASSWORD`, `CONFIG_SERVER_USERNAME/PASSWORD`.
- Start them **before** the services: a service refuses to start without its configuration.
- Tests use neither (test profile), so `mvn verify` still needs no running services.

## Local prerequisites

- Java 21
- Maven 3.9+ (`brew install maven`) or IntelliJ's bundled Maven
- PostgreSQL on `localhost:5432` (pgAdmin for inspecting data)
- Kafka on `localhost:9092` (below)
- Redis on `localhost:6379` (below)
- discovery-server (8761) and config-server (8888) from this repository, started first
- Mailpit on `localhost:1025` / inbox http://localhost:8025 (below)
- Postman

## Kafka (local, no Docker)

product-service and inventory-service exchange events through Kafka. Homebrew's Kafka runs in KRaft mode
(no ZooKeeper) and is ready to use after install:

```bash
brew install kafka
brew services start kafka                     # starts now and at every login; stop: brew services stop kafka

kafka-topics --bootstrap-server localhost:9092 --list    # check it's up (topics appear once services start)
```

Watch events live while you use Postman (one terminal per topic):

```bash
kafka-console-consumer --bootstrap-server localhost:9092 --topic product-events   --from-beginning --property print.key=true
kafka-console-consumer --bootstrap-server localhost:9092 --topic inventory-events --from-beginning --property print.key=true
```

Topics (created by the services on startup, 3 partitions, key = productId so each product's events stay in order):
`product-events`, `inventory-events`, `order-events`, `payment-events` (both keyed by orderId), and dead-letter topics
`product-events.DLT`, `inventory-events.DLT`, `order-events.DLT`, `payment-events.DLT` (plus notification-service's own
`order-events.notification.DLT`, `payment-events.notification.DLT`, `wishlist-events.notification.DLT`,
wishlist-service's `product-events.wishlist.DLT`, review-service's `order-events.review.DLT`, product-service's
`review-events.DLT`, shipping-service's `order-events.shipping.DLT`, order-service's `shipment-events.DLT`,
notification-service's `shipment-events.notification.DLT`, and search-service's / recommendation-service's /
admin-service's `<topic>.search.DLT` / `<topic>.recommendation.DLT` / `<topic>.admin.DLT` for each topic they read) for
messages that could not be processed.
`wishlist-events` (key = user id) carries price-drop alerts from wishlist-service to notification-service.
`review-events` (key = product id) carries each product's new rating from review-service to product-service.
`shipment-events` (key = order id) carries shipped / out for delivery / missed delivery / delivered from shipping-service to
order-service and notification-service.
`category-events` (key = category id) carries category changes from product-service to search-service, which builds its
search index from product-, category-, inventory- and review-events. recommendation-service reads the same four plus
order-events (paid and cancelled orders). admin-service builds the admin dashboard from order-, payment-, inventory- and
shipment-events. If Kafka is stopped, the services keep working: events wait in each
service's `outbox_events` table and are sent when Kafka is back.

Integration tests start their own in-memory Kafka, so `mvn verify` doesn't need Kafka running.

## Redis (local, no Docker)

cart-service stores carts in Redis.

```bash
brew install redis
brew services start redis      # starts now and at every login; stop: brew services stop redis
redis-cli ping                 # → PONG
```

Dev carts live in database 0; cart-service's integration tests use database 15 and empty it before each test.
Unlike Kafka's console consumer, `redis-cli` commands return straight away (see `cart-service/README.md` for
how to look at carts).

## Mailpit (local email, no Docker)

notification-service sends customer emails over SMTP. Locally, Mailpit catches them all (nothing reaches a real
inbox) and shows them in a web inbox.

```bash
brew install mailpit
brew services start mailpit    # SMTP on localhost:1025; stop: brew services stop mailpit
open http://localhost:8025     # the inbox
```

notification-service's tests use a recording mail sender, so `mvn verify` doesn't need Mailpit.

---

# user-service

Registration, login (JWT), profile and admin user management.

## 1. Create the databases (once)

pgAdmin → Query Tool on the `postgres` database:

```sql
CREATE DATABASE ecommerce_user_db;
CREATE DATABASE ecommerce_user_test_db;
```

Flyway creates the tables on startup. Hibernate only validates them (`ddl-auto: validate`).

## 2. Environment (.env at the repository root)

Configuration lives in each service's `src/main/resources/application.properties` (committed).
Secrets live in **one** `.env` at the repository root, shared by all services. It is git-ignored and
loaded automatically, so no IntelliJ environment variables are needed. In the Docker phase, Docker
Compose reads the same file and passes the values to containers as environment variables.

```properties
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your-postgres-password
JWT_SECRET=base64-key-from-openssl-rand-base64-48
ADMIN_EMAIL=admin@ecommerce.com
ADMIN_PASSWORD=Admin@12345
EUREKA_USERNAME=eureka
EUREKA_PASSWORD=letters-and-digits-from-openssl-rand-hex-16
CONFIG_SERVER_USERNAME=config
CONFIG_SERVER_PASSWORD=letters-and-digits-from-openssl-rand-hex-16
```

After a fresh clone: `cp .env.example .env` and fill it in. Format is plain `KEY=VALUE`: no quotes,
no spaces around `=`. Real environment variables override the file. Changing `JWT_SECRET` invalidates
all issued tokens, and it must be identical for user-service and api-gateway.

The admin is created only if that email doesn't exist yet, so it's safe to leave it set.

## 3. Run

Run `UserServiceApplication` in IntelliJ, or:

```bash
cd user-service
mvn spring-boot:run
```

- Swagger UI: http://localhost:8081/swagger-ui.html (click **Authorize**, paste the token without `Bearer `)
- Health: http://localhost:8081/actuator/health

## 4. Test

```bash
mvn test     # unit tests only (no database needed)
mvn verify   # unit + integration tests against ecommerce_user_test_db
```

Integration tests (`*IT.java`) wipe the `users` table in `ecommerce_user_test_db` before each test.
They never touch `ecommerce_user_db`. They use the DB password from the root `.env`
and their own test-only JWT key (`src/test/resources/application-test.properties`).

## 5. Postman

Import `user-service/postman/user-service.postman_collection.json` (its `adminEmail`/`adminPassword`
variables already match the defaults in `.env`), and run the requests top to bottom. Login requests save
tokens into collection variables automatically.

## API

| Method | Path                              | Access          |
|--------|-----------------------------------|-----------------|
| POST   | `/api/v1/auth/register`           | public          |
| POST   | `/api/v1/auth/login`              | public          |
| GET    | `/api/v1/users/me`                | CUSTOMER, ADMIN |
| PUT    | `/api/v1/users/me`                | CUSTOMER, ADMIN |
| GET    | `/api/v1/admin/users`             | ADMIN           |
| GET    | `/api/v1/admin/users/{id}`        | ADMIN           |
| PUT    | `/api/v1/admin/users/{id}/status` | ADMIN           |

`GET /api/v1/admin/users` supports `role`, `status`, `page`, `size`, `sort` (e.g. `?role=CUSTOMER&sort=createdAt,desc`).

## Design notes

- **Passwords**: BCrypt (cost 12). Never returned, never logged.
- **JWT**: HS256, contains `sub` (userId), `email`, `role`, `iss`, `iat`, `exp`. Stateless; no sessions.
- **Suspension takes effect immediately**: the JWT filter re-loads the user on each request, so a
  suspended user's existing token stops working at once.
- **Login errors**: unknown email and wrong password return the same 401 message (and take the same
  time), so registered emails can't be discovered. Account status is only revealed after a correct password.
- **Emails** are stored lower-cased; `Omesh@Example.com` and `omesh@example.com` are the same account.
- **Optimistic locking** (`version` column) prevents silent lost updates.
- **Errors** always use one shape: `timestamp, status, error, message, path` (+ `validationErrors`).

## Git workflow

- `main` — releasable code
- `develop` — integration branch
- `feature/<name>` — one branch per feature, merged into `develop` via PR

Commit messages follow Conventional Commits: `feat:`, `fix:`, `test:`, `refactor:`, `chore:`, `docs:`.

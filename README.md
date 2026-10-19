# E-Commerce Platform (Amazon-like) — Spring Boot Microservices

Built incrementally, phase by phase. Every service runs and is tested **locally first**;
Docker/Docker Compose is added only after all services work locally.

## Services

| Service         | Port | Database               | Status             |
|-----------------|------|------------------------|--------------------|
| api-gateway     | 8080 | —                      | Phase 1 — complete |
| user-service    | 8081 | `ecommerce_user_db`    | Phase 1 — complete |
| product-service | 8082 | `ecommerce_product_db` | Phase 2 — complete |

All client traffic goes through the gateway on **8080** (see `api-gateway/README.md`).
Start order: PostgreSQL → user-service → product-service → api-gateway.
Each service has its own README with APIs, run/test steps and a Postman collection.

## Local prerequisites

- Java 21
- Maven 3.9+ (`brew install maven`) or IntelliJ's bundled Maven
- PostgreSQL on `localhost:5432` (pgAdmin for inspecting data)
- Postman

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

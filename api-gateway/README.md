# api-gateway

Single entry point for all clients on **port 8080**. Built on Spring Cloud Gateway (reactive/Netty).

```
Postman / frontend ──► api-gateway :8080 ──► user-service :8081
                                          ├──► product-service :8082
                                          ├──► inventory-service :8083
                                          ├──► cart-service :8084
                                          ├──► order-service :8085
                                          └──► payment-service :8086
                         │
                         ├─ CORS (preflight answered here)
                         ├─ JWT check (signature, expiry, issuer)
                         ├─ ADMIN role check for /api/v1/admin/**, /api/v1/inventory/**, /api/v1/orders/admin/**, /api/v1/payments/admin/**
                         ├─ X-User-Id / X-User-Email / X-User-Role headers to services
                         ├─ X-Correlation-Id on every request and response
                         └─ JSON errors: 401, 403, 404, 503, 504
```

## Routes

| Path                    | Service         | Access                                      |
|-------------------------|-----------------|---------------------------------------------|
| `/api/v1/auth/**`       | user-service    | public                                      |
| `/api/v1/users/**`      | user-service    | valid JWT                                   |
| `/api/v1/admin/**`      | user-service    | valid JWT, ADMIN                            |
| `/api/v1/products/**`   | product-service | GET public; POST/PUT/DELETE need ADMIN      |
| `/api/v1/categories/**` | product-service | GET public; POST/PUT/DELETE need ADMIN      |
| `/api/v1/inventory/**`  | inventory-service | valid JWT, ADMIN (every method)           |
| `/api/v1/cart/**`       | cart-service    | valid JWT (any role); the cart is the caller's own |
| `/api/v1/orders/**`     | order-service   | valid JWT; customers see only their own orders |
| `/api/v1/orders/admin/**` | order-service | valid JWT, ADMIN                            |
| `/api/v1/payments/**`   | payment-service | valid JWT; customers pay/see only their own orders |
| `/api/v1/payments/admin/**` | payment-service | valid JWT, ADMIN                        |

New services are added as routes in `config/GatewayRoutesConfig.java`.

## Run

1. Start **user-service** (8081), **product-service** (8082), **inventory-service** (8083), **cart-service** (8084), **order-service** (8085) and **payment-service** (8086) first.
2. Start the gateway: run `ApiGatewayApplication` in IntelliJ, or `mvn spring-boot:run` from this folder.
3. Check http://localhost:8080/actuator/health → `{"status":"UP"}`.
4. Check routes: http://localhost:8080/actuator/gateway/routes

Configuration comes from the root `.env` (shared with user-service). The gateway needs only `JWT_SECRET`
(same value as user-service). Optional: `USER_SERVICE_URL` (default `http://localhost:8081`),
`PRODUCT_SERVICE_URL` (default `http://localhost:8082`), `INVENTORY_SERVICE_URL` (default `http://localhost:8083`),
`CART_SERVICE_URL` (default `http://localhost:8084`), `ORDER_SERVICE_URL` (default `http://localhost:8085`), `PAYMENT_SERVICE_URL` (default `http://localhost:8086`),
`CORS_ALLOWED_ORIGINS`.

## Test

```bash
mvn test     # no database or running user-service needed
```

Tests start the real gateway against a stub downstream server and cover: public paths, 401 (missing,
malformed, expired, wrong-key and wrong-issuer tokens), 403 for customers on admin paths, header
spoofing, path traversal, 404, 503 when the service is down, correlation ids and CORS.

## Using it from Postman

Use `http://localhost:8080` instead of `http://localhost:8081`. Every endpoint, body and token is the same.
In the user-service collection, change the `baseUrl` collection variable to `http://localhost:8080` and
run the collection again; all requests should pass exactly as before.

## Design notes

- **Defence in depth**: the gateway validates the token, but still forwards the `Authorization` header and
  user-service validates it again. user-service also rejects suspended accounts, which the gateway can't know.
- **Identity headers can't be spoofed**: client-supplied `X-User-*` headers are always removed and replaced
  with values from the verified token.
- **Same error format** as user-service: `timestamp, status, error, message, path`.
- **Never logged**: tokens, headers, query strings.

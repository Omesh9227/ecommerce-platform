# config-repo

Settings served by [config-server](../config-server) (port 8888). **No secrets here** - this folder is in Git;
use `${PLACEHOLDERS}`, which each service fills in from `.env` (a test fails the build otherwise).

| File | For | Contains |
|------|-----|----------|
| `application.properties` | every service | `shop.currency`, `shop.payment-window-minutes`, Eureka registration |
| `product-service.properties` | product-service | catalog currency |
| `inventory-service.properties` | inventory-service | default low-stock threshold |
| `cart-service.properties` | cart-service | max items, max quantity per item, cart expiry |
| `order-service.properties` | order-service | shipping fee, free-shipping threshold, payment window, expiry timings |
| `payment-service.properties` | payment-service | payment provider (`simulated`) |
| `notification-service.properties` | notification-service | sender address and name, store name, support email |
| `wishlist-service.properties` | wishlist-service | max saved products, price-drop email threshold (%) |
| `review-service.properties` | review-service | whether only buyers may review |
| `shipping-service.properties` | shipping-service | warehouse PIN code and time zone, delivery days by distance, carriers |
| `search-service.properties` | search-service | price filter ranges, page size, suggestions, popular-search window |
| `recommendation-service.properties` | recommendation-service | list sizes, trending window, view history kept |

A service's own file wins over `application.properties`; both win over the service's
`src/main/resources/application.properties`.

**To change a value**: edit the file, restart that service (no rebuild), commit. Check what a service receives:
`curl -u config:<CONFIG_SERVER_PASSWORD> http://localhost:8888/order-service/default`

Values used by several services are defined once: `shop.payment-window-minutes=30` is both the time order-service
waits before cancelling an unpaid order and the "pay within 30 minutes" in notification-service's email.

**Tests** don't read this folder (except config-server's own tests, which check it): each service's
`src/test/resources/application-test.properties` has its own copy of the values it needs.

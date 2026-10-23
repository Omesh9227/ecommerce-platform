# discovery-server

**Eureka** service registry on **port 8761**. Every service registers here when it starts and is removed when it
stops, so nobody needs to know host names and ports:

```
                       ┌──────────── discovery-server :8761 (Eureka) ◄──── every service registers (name, host, port)
                       │  "where is order-service?"
Postman ──► api-gateway :8080 ── lb://order-service ──► order-service :8085 ── lb://inventory-service ──► inventory-service
                                                                         └── lb://cart-service, lb://product-service
```

- The **gateway** routes `/api/v1/orders/**` to `lb://order-service`: it asks Eureka for the running instances and
  spreads requests over them (start a second order-service on another port and both get traffic).
- **order-service** and **cart-service** call `lb://cart-service`, `lb://product-service`, `lb://inventory-service`
  the same way.
- A service with no running instance gives a clean JSON **503** at the gateway.

## Run

1. Add to the root `.env` (letters and digits only, they become part of a URL):
   ```properties
   EUREKA_USERNAME=eureka
   EUREKA_PASSWORD=<openssl rand -hex 16>
   ```
2. Start it **first**: `DiscoveryServerApplication` in IntelliJ, or `mvn spring-boot:run` here.
3. Dashboard: **http://localhost:8761** (log in with the values above). After starting the other services, each
   appears within ~10 s as `ORDER-SERVICE  UP (1) - order-service:8085`.

Registry as JSON: `curl -u eureka:<password> -H 'Accept: application/json' http://localhost:8761/eureka/apps`

## Test

```bash
mvn verify   # starts the real registry on a random port; nothing else needed
```

Covered: login required (registry, dashboard, registration), wrong password, public health, and a service
registering, being listed and deregistering over the same REST API the services use.

## Postman

`discovery-server/postman/discovery-and-config.postman_collection.json` checks Eureka, the Config Server and the
gateway's routes together. Set the collection variables `eurekaPassword` and `configPassword` to the values in
your `.env` first.

## Design notes

- **Login**: HTTP basic from `.env`. Services send it inside their Eureka URL, which comes from
  `config-repo/application.properties` (`http://${EUREKA_USERNAME}:${EUREKA_PASSWORD}@localhost:8761/eureka/`) -
  the placeholders are filled in by each service, so the password is never written in Git.
- **Fast feedback locally**: services renew every 10 s; stopped ones are evicted after ~30 s (self-preservation is
  off - in production with several registries it's turned on with `EUREKA_SELF_PRESERVATION=true`).
- **One registry** for now; in Kubernetes, Kubernetes' own service discovery can replace Eureka.

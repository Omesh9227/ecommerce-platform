# config-server

**Spring Cloud Config Server** on **port 8888**. Serves the files in [`config-repo/`](../config-repo) to the services
at startup:

| File in config-repo/                 | Given to |
|--------------------------------------|----------|
| `application.properties`             | every service (Eureka settings, `shop.currency`, `shop.payment-window-minutes`) |
| `<service-name>.properties`          | that service only, wins over `application.properties` (order rules, cart limits, email sender...) |

Each service keeps its technical settings (port, database, Kafka, security) in its own
`src/main/resources/application.properties`; config-repo has the settings you change **without rebuilding**.

## Run

1. Add to the root `.env`:
   ```properties
   CONFIG_SERVER_USERNAME=config
   CONFIG_SERVER_PASSWORD=<openssl rand -hex 16>
   ```
2. Start discovery-server, then **config-server** (`ConfigServerApplication`), then the services.
   A service started while config-server is down stops at once with
   `Could not locate PropertySource ... failing` - start config-server and run it again.
3. See what a service gets:
   ```bash
   curl -u config:<password> http://localhost:8888/order-service/default
   ```

### Change a setting

Edit e.g. `config-repo/order-service.properties` (`app.order.shipping-fee=50.00`), restart **order-service**
(no rebuild, config-server keeps running - it reads the files on every request). Then commit the change like code.

## Test

```bash
mvn verify   # serves the real config-repo/ on a random port
```

Covered: every service gets the shared settings, each gets its own and not another's, login required, and two
guards on config-repo itself: no secret written in it, and no misspelled file name (`order-servce.properties`
would never be served).

## Design notes

- **Backend**: `native` - the `config-repo/` folder of this repository, so config is versioned and reviewed with
  the code (PRs). It can move to its own Git repository later (`git` profile + `spring.cloud.config.server.git.uri`)
  without changing any service.
- **No secrets in config-repo**: passwords stay `${PLACEHOLDERS}`. The server hands them over unresolved and each
  service fills them in from its own environment / `.env`.
- **Login**: HTTP basic from `.env`; the services send `spring.cloud.config.username/password`.
- **Fail fast**: services *need* their config (e.g. order rules), so they refuse to start without it rather than
  run with missing values. Tests never use the Config Server (`spring.cloud.config.enabled=false` in the test
  profile; the import is in a `#---` section that is skipped for the `test` profile).
- **Restart to apply**: changes take effect on restart. Live reload (`/actuator/refresh`, Spring Cloud Bus) can come
  later; it's not worth the complexity yet.

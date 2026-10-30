# frontend: the Cartwheel storefront and admin panel

The customer website on **http://localhost:3000**, and the staff's admin panel on **http://localhost:3000/admin**: one
React app that talks only to the **api-gateway** (8080). Customers find products (search with typo correction, filters with counts, suggestions as they type), read and
write reviews, get recommendations, fill a cart, check out with an address, pay (simulated card or UPI), follow
their orders and parcels, cancel and get refunds, keep a wishlist and edit their profile. Staff watch sales, ship
orders, run the catalog and stock, moderate reviews and manage accounts.

```
Browser ──► Vite dev server :3000 ──/api/**──► api-gateway :8080 ──► user, product, search, recommendation, review,
            (the React app)        (proxy)                          cart, order, payment, shipping, wishlist services
```

The dev server forwards every `/api/...` call to the gateway, so the browser only ever talks to one address: no CORS
setup, and the app's code uses the same relative paths it will use behind nginx in the Docker phase.

## Pages

| Page | Address | Uses |
|------|---------|------|
| Home | `/` | trending, recommended for you, recently viewed, popular searches, categories |
| Search and categories | `/search?q=&categoryId=&brand=&minPrice=&maxPrice=&minRating=&inStock=&sort=&page=` | search-service (results, facets, corrected query); suggestions in the header |
| Product | `/products/:id` | product, stock, delivery estimate by PIN code, add to cart, wishlist, rating summary, reviews (filter, sort, helpful votes, write), bought together, similar, also viewed; records the view |
| Cart | `/cart` | quantities, price changes since added, stock problems, save for later |
| Checkout | `/checkout` | delivery address (remembered on this device), delivery estimate, places the order |
| Pay | `/orders/:number/pay` | card or UPI, the time left to pay, declined attempts |
| Orders | `/orders`, `/orders/:number` | status filter; progress, parcel tracking, payment and refund, history, cancel |
| Track a parcel | `/track/:trackingNumber` | public, no sign-in |
| Wishlist | `/wishlist` | price drops, move to cart |
| Account | `/account` | profile, your reviews (edit, delete), viewing history (clear) |
| Sign in, register | `/login`, `/register` | the same rules as user-service, checked before sending |

Cart, checkout, orders, wishlist and account need an account: guests are sent to sign in and brought back.

## Admin panel

Sign in with an **ADMIN** account (`admin@ecommerce.com` / `Admin@12345` locally, created by user-service at startup):
the header shows an **Admin** link. Customers who open `/admin` are told it's for staff, and the gateway refuses their
calls anyway (403). The admin code is a separate download (about 17 KB gzipped), so shoppers never load it.

| Page | Address | Uses |
|------|---------|------|
| Dashboard | `/admin?range=today\|7d\|30d\|90d` or `?range=custom&from=&to=` | admin-service: waiting work (to ship, on the way, low stock, missed deliveries), net sales with the change on the period before, sales per day (chart or table, CSV download), orders, payments, buyers, best sellers, orders right now, low stock, activity feed (warnings only) |
| Orders | `/admin/orders?status=&userId=`, `/admin/orders/:number` | order-service (all orders, cancel with a reason and refund), payment-service, shipping-service, notification-service (the emails the customer got) |
| Shipments | `/admin/shipments?status=&orderNumber=`, `/admin/shipments/:id` | shipping-service: oldest first; pack, hand to a carrier, record scans (in transit needs a hub, a failed delivery needs a reason) |
| Products | `/admin/products?q=&categoryId=&status=`, `/admin/products/new`, `/admin/products/:id` | product-service: every status, add and edit (the service's rules checked first), hide, discontinue |
| Categories | `/admin/categories` | product-service: the tree; add, rename, move, delete (refused while it has sub-categories or products) |
| Inventory | `/admin/inventory?q=&low=1&product=` | inventory-service: count the shelf, add or remove, low-stock threshold, recent movements |
| Reviews | `/admin/reviews?status=&productId=` | review-service: hide with a reason the author sees, show again, delete |
| Customers | `/admin/customers?role=&status=` | user-service: accounts, their orders, suspend (signs them out) and reactivate; never your own |

Filters and pages live in the address, so a view can be bookmarked or shared. Actions that can't be undone ask a second
time in place.

## Run

Needs **Node.js 22** (`brew install node@22`) and the backend running (all services and the gateway, as in the root
README).

```bash
cd frontend
npm install          # once, and after pulling changes to package.json
npm run dev          # http://localhost:3000
```

Settings (optional) go in `frontend/.env.local` (git-ignored), see `.env.example`:

| Variable | Default | |
|----------|---------|---|
| `VITE_API_TARGET` | `http://localhost:8080` | where `/api` is forwarded (the gateway) |
| `PORT` | `3000` | dev server port |

`npm run build` makes the production files in `dist/` (type-checked first); `npm run preview` serves them on 3000
with the same `/api` forwarding.

Sign in with a customer you registered (or register on the site). Payments are simulated:
**4242 4242 4242 4242** is accepted, **4000 0000 0000 0002** is declined, **4000 0000 0000 9995** has insufficient
funds (any future expiry, any CVV); UPI IDs starting with `fail` are declined.

## Test

```bash
npm test                     # unit and component tests (Vitest + Testing Library), no backend needed
npm run typecheck            # TypeScript

npx playwright install chromium   # once: the browser for the end-to-end tests
npm run e2e                  # browser tests against a built-in mock API (starts it and the app itself)
npm run e2e:live             # the same browser tests against your real services
```

**Unit and component tests** (40): money and date formats, the form rules (registration, address, card with Luhn
check and expiry, UPI), reading the JWT, the API client (envelopes, field errors, 401 ending the session, no
connection), and pages with a fake gateway: search filters going into the address, adding to the cart as a guest
and with too little stock, changing quantities, a declined card then a payment with a new Idempotency-Key, a mistyped
card never sent, cancelling a paid order. Admin: who may enter, the product rules and a refused SKU shown on its
field, shipping with a carrier, scans that need a hub or a reason, stock changes, suspending (not yourself, and only
after a second click), and the dashboard with its chart as a table.

**Browser tests** (Playwright, 6 journeys: 3 for customers, 3 for staff):

1. A new customer registers, searches with a typo (`walnot lamp` → walnut), opens the product, checks delivery to a
   PIN code, adds 2 to the cart and saves it to the wishlist, checks out (a missing address is caught first), is
   declined with one card and pays with another, sees the order confirmed, the warehouse ships it and the carrier
   delivers it (done through the admin API), follows the parcel on the public tracking page, writes a review that
   becomes a **verified purchase**, and finds the order delivered and the item in the wishlist.
2. A customer pays by UPI and cancels: the order is cancelled with the reason and the payment shows the refund.
3. A guest opening the cart is sent to sign in (a wrong password first) and comes back to the cart.
4. Staff sign in at `/admin`, add, rename and delete a category, add a product (a missing category and a bad SKU are
   caught first), count its stock and set its low-stock warning. A customer buys 3; the dashboard's activity shows the
   order paid and the product shows as low on stock. Staff open the order and its payment, pack it, ship it with Blue
   Dart, record the scans (a hub is required) until it's delivered, hide the buyer's review and show it again, suspend
   the buyer (whose sign-in is then refused), reactivate them and find their order.
5. Staff cancel a paid order with a reason; the customer is refunded.
6. A customer can't open the admin area.

`npm run e2e` runs them against `e2e/mock-api/server.mjs`, an in-memory stand-in for the gateway with the same paths,
answers and rules (including Kafka-like delays). `npm run e2e:live` runs them against `http://localhost:3000`
(started for you if it isn't running) and the real services, waiting where events travel through Kafka. It creates
its products (and a short-lived test category) in a **Browser tests** category, and uses the admin login `admin@ecommerce.com` / `Admin@12345`
(change with `E2E_ADMIN_EMAIL` / `E2E_ADMIN_PASSWORD`). A failed run leaves a report: `npx playwright show-report`.

## How it works

- **React 18 + TypeScript**, built with **Vite**. **React Router** for pages, **TanStack Query** for server data
  (caching, retries, polling), **Tailwind CSS** for styles. No state library: server data lives in the query cache,
  filters live in the address.
- **API client** (`src/api/client.ts`): every call returns the envelope's `data`; errors become `ApiError` with the
  service's message and field errors, shown next to the fields. The paths are in `src/api/shop.ts`, the response
  shapes in `src/api/types.ts`.
- **Sign-in**: the JWT from user-service is kept in `localStorage` and sent as `Authorization: Bearer`. The app reads
  its expiry and signs out just before it; a 401 from the gateway signs out too, and the next sign-in returns to the
  page. Customer data in the cache is dropped on sign-in and sign-out.
- **Things that happen through Kafka** (the payment opening after checkout, the order confirming after payment, the
  parcel moving, the refund) are shown by polling: the pay page waits for the payment, an order page refreshes every
  few seconds until the order is delivered or cancelled.
- **Never twice**: checkout sends an `Idempotency-Key` that is kept if the connection drops (a retry returns the same
  order) and renewed after a clear answer; every payment attempt has its own key.
- **Card details** go straight to payment-service and are never stored or logged by the app.
- **Design**: deep slate and marigold, Bricolage Grotesque for headings and prices, Public Sans for text. Prices are
  the loudest thing on a product. Works from phone width up, keyboard focus is visible, motion respects reduced-motion
  settings. Products without photos get a tile with their initials.

## Layout

```
frontend/
├─ src/
│  ├─ api/          client, endpoints, types
│  ├─ auth/         sign-in state, JWT reading, signed-in-only and staff-only pages
│  ├─ admin/        the admin panel: layout, charts, shared pieces, pages/ (one file per screen)
│  ├─ components/   header, search box, prices, stars, product tiles, form fields, feedback
│  ├─ pages/        one file per page (+ product/Reviews.tsx); *.test.ts(x) next to them
│  └─ lib/          formatting, validation, storage
├─ e2e/             Playwright journeys (storefront, admin), API helper, mock API
└─ vite.config.ts   dev server, /api proxy, Vitest settings
```

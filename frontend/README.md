# frontend: the Cartwheel storefront

The customer website on **http://localhost:3000**: a React app that talks only to the **api-gateway** (8080).
Customers find products (search with typo correction, filters with counts, suggestions as they type), read and
write reviews, get recommendations, fill a cart, check out with an address, pay (simulated card or UPI), follow
their orders and parcels, cancel and get refunds, keep a wishlist and edit their profile.

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

**Unit and component tests** (30): money and date formats, the form rules (registration, address, card with Luhn
check and expiry, UPI), reading the JWT, the API client (envelopes, field errors, 401 ending the session, no
connection), and pages with a fake gateway: search filters going into the address, adding to the cart as a guest
and with too little stock, changing quantities, a declined card then a payment with a new Idempotency-Key, a mistyped
card never sent, cancelling a paid order.

**Browser tests** (Playwright, 3 journeys):

1. A new customer registers, searches with a typo (`walnot lamp` → walnut), opens the product, checks delivery to a
   PIN code, adds 2 to the cart and saves it to the wishlist, checks out (a missing address is caught first), is
   declined with one card and pays with another, sees the order confirmed, the warehouse ships it and the carrier
   delivers it (done through the admin API), follows the parcel on the public tracking page, writes a review that
   becomes a **verified purchase**, and finds the order delivered and the item in the wishlist.
2. A customer pays by UPI and cancels: the order is cancelled with the reason and the payment shows the refund.
3. A guest opening the cart is sent to sign in (a wrong password first) and comes back to the cart.

`npm run e2e` runs them against `e2e/mock-api/server.mjs`, an in-memory stand-in for the gateway with the same paths,
answers and rules (including Kafka-like delays). `npm run e2e:live` runs them against `http://localhost:3000`
(started for you if it isn't running) and the real services, waiting where events travel through Kafka. It creates
its products in a **Browser tests** category, and uses the admin login `admin@ecommerce.com` / `Admin@12345`
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
│  ├─ auth/         sign-in state, JWT reading, signed-in-only pages
│  ├─ components/   header, search box, prices, stars, product tiles, form fields, feedback
│  ├─ pages/        one file per page (+ product/Reviews.tsx); *.test.ts(x) next to them
│  └─ lib/          formatting, validation, storage
├─ e2e/             Playwright journeys, admin helper, mock API
└─ vite.config.ts   dev server, /api proxy, Vitest settings
```

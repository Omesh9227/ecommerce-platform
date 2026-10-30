import { expect, type APIRequestContext, type Page } from '@playwright/test';

/**
 * What the shop's staff (and the other services) do, through the gateway's API - so the browser tests can focus on
 * the customer. The same calls work against the mock API and the real services; with the real services, steps that
 * travel through Kafka are waited for.
 */

const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'admin@ecommerce.com';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'Admin@12345';
const POLL = { timeout: 60_000, intervals: [500, 1000, 2000] };

export const runTag = () => String(Date.now()).slice(-6);

export const ADMIN = { email: ADMIN_EMAIL, password: ADMIN_PASSWORD };

export interface Customer {
  email: string;
  password: string;
  token: string;
}

async function data<T>(response: Awaited<ReturnType<APIRequestContext['get']>>): Promise<T> {
  const body = await response.json().catch(() => null);
  expect(response.ok(), `${response.url()} → ${response.status()} ${JSON.stringify(body)}`).toBeTruthy();
  return body?.data as T;
}

export class Backend {
  private adminToken = '';

  constructor(private readonly request: APIRequestContext) {}

  private auth() {
    return { Authorization: `Bearer ${this.adminToken}` };
  }

  async loginAdmin() {
    const res = await this.request.post('/api/v1/auth/login', { data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD } });
    this.adminToken = (await data<{ accessToken: string }>(res)).accessToken;
  }

  /** A new product with stock, ready to be found by search and put in a cart. */
  async createProduct(opts: { name: string; brand?: string; price: number; stock: number }): Promise<string> {
    if (!this.adminToken) await this.loginAdmin();
    const tag = runTag();
    const category = await this.testCategory();
    const product = await data<{ id: string }>(
      await this.request.post('/api/v1/products', {
        headers: this.auth(),
        data: {
          sku: `E2E-${tag}-${Math.floor(Math.random() * 1000)}`,
          name: opts.name,
          brand: opts.brand ?? 'Cartwheel Test',
          description: 'Made for the storefront browser tests.',
          categoryId: category.id,
          price: opts.price,
          discountPercent: 0,
          imageUrls: [],
        },
      }),
    );
    // inventory-service creates the item when it hears about the product (Kafka)
    await expect.poll(async () => (await this.request.get(`/api/v1/inventory/${product.id}`, { headers: this.auth() })).status(), POLL).toBe(200);
    await data(await this.request.put(`/api/v1/inventory/${product.id}/stock`, { headers: this.auth(), data: { quantityOnHand: opts.stock } }));
    // product-service (for the cart) and search-service learn the stock from events too
    await expect
      .poll(async () => (await (await this.request.get(`/api/v1/products/${product.id}`)).json())?.data?.availableQuantity, POLL)
      .toBe(opts.stock);
    await expect
      .poll(async () => {
        const res = await this.request.get(`/api/v1/search/products?q=${encodeURIComponent(opts.name)}&facets=false`);
        const hits: { productId: string; inStock: boolean }[] = (await res.json())?.data?.results?.content ?? [];
        return hits.some((h) => h.productId === product.id && h.inStock);
      }, POLL)
      .toBe(true);
    return product.id;
  }

  /** One category for everything the tests create, so they don't fill the shop's menu. */
  async testCategory(): Promise<{ id: string }> {
    if (!this.adminToken) await this.loginAdmin();
    const tree = await data<{ id: string; name: string }[]>(await this.request.get('/api/v1/categories'));
    const existing = tree.find((c) => c.name === 'Browser tests');
    if (existing) return existing;
    return data<{ id: string }>(
      await this.request.post('/api/v1/categories', {
        headers: this.auth(),
        data: { name: 'Browser tests', description: 'Products made by the storefront browser tests' },
      }),
    );
  }

  /** The warehouse packs and ships the order, the carrier scans it on the way and delivers it. */
  async shipAndDeliver(orderNumber: string) {
    if (!this.adminToken) await this.loginAdmin();
    let shipmentId = '';
    await expect
      .poll(async () => {
        const res = await this.request.get(`/api/v1/shipments/admin?orderNumber=${orderNumber}`, { headers: this.auth() });
        shipmentId = (await res.json())?.data?.content?.[0]?.id ?? '';
        return shipmentId;
      }, POLL)
      .not.toBe('');
    const post = async (path: string, body?: object) =>
      data(await this.request.post(`/api/v1/shipments/admin/${shipmentId}/${path}`, { headers: this.auth(), data: body ?? {} }));
    await post('pack');
    await post('ship', { carrier: 'Delhivery', location: 'Bengaluru' });
    await post('tracking', { status: 'IN_TRANSIT', location: 'Bengaluru hub' });
    await post('tracking', { status: 'OUT_FOR_DELIVERY', location: 'Koramangala' });
    await post('tracking', { status: 'DELIVERED', location: 'Koramangala' });
  }

  /** Waits until a product the staff added in the browser has its stock item (inventory-service hears of it by Kafka). */
  async waitForStockItem(productId: string) {
    if (!this.adminToken) await this.loginAdmin();
    await expect.poll(async () => (await this.request.get(`/api/v1/inventory/${productId}`, { headers: this.auth() })).status(), POLL).toBe(200);
  }

  /** Waits until the shop (product-service, which the cart asks) sells this many of a product. */
  async waitUntilBuyable(productId: string, quantity: number) {
    await expect
      .poll(async () => (await (await this.request.get(`/api/v1/products/${productId}`)).json())?.data?.availableQuantity ?? 0, POLL)
      .toBeGreaterThanOrEqual(quantity);
  }

  /** A new customer who buys a product and pays by card, all through the API. */
  async customerBuys(productId: string, quantity: number, firstName = 'Meera'): Promise<Customer & { orderNumber: string }> {
    const customer = await this.newCustomer(firstName);
    const auth = { Authorization: `Bearer ${customer.token}` };
    await data(await this.request.post('/api/v1/cart/items', { headers: auth, data: { productId, quantity } }));
    const order = await data<{ orderNumber: string }>(
      await this.request.post('/api/v1/orders/checkout', {
        headers: { ...auth, 'Idempotency-Key': crypto.randomUUID() },
        data: {
          shippingAddress: { fullName: `${firstName} Iyer`, phone: '9876543210', line1: '4 Lake View Road', city: 'Chennai', state: 'Tamil Nadu', postalCode: '600028' },
        },
      }),
    );
    // payment-service opens the payment when it hears of the order (Kafka)
    await expect.poll(async () => (await this.request.get(`/api/v1/payments/orders/${order.orderNumber}`, { headers: auth })).status(), POLL).toBe(200);
    await data(
      await this.request.post(`/api/v1/payments/orders/${order.orderNumber}/pay`, {
        headers: { ...auth, 'Idempotency-Key': crypto.randomUUID() },
        data: { method: 'CARD', card: { number: '4242424242424242', expiryMonth: 12, expiryYear: 2030, cvv: '123', holderName: `${firstName} Iyer` } },
      }),
    );
    // order-service confirms the order when the payment event arrives
    await expect
      .poll(async () => (await (await this.request.get(`/api/v1/orders/${order.orderNumber}`, { headers: auth })).json())?.data?.status, POLL)
      .toBe('CONFIRMED');
    return { ...customer, orderNumber: order.orderNumber };
  }

  /** The customer reviews a product (review-service accepts it once it knows the product). */
  async customerReviews(customer: Customer, productId: string, title: string) {
    await expect
      .poll(
        async () =>
          (
            await this.request.post(`/api/v1/reviews/products/${productId}`, {
              headers: { Authorization: `Bearer ${customer.token}` },
              data: { rating: 4, title, comment: 'Sturdy and holds a lot of books.' },
            })
          ).status(),
        POLL,
      )
      .toBe(201);
  }

  /** The HTTP status a sign-in with this customer's password gets (200, or 401/403 when blocked). */
  async signInStatus(customer: Customer): Promise<number> {
    return (await this.request.post('/api/v1/auth/login', { data: { email: customer.email, password: customer.password } })).status();
  }

  private async newCustomer(firstName: string): Promise<Customer> {
    const email = `${firstName.toLowerCase()}${Date.now()}@example.com`;
    const password = 'Password@123';
    await data(await this.request.post('/api/v1/auth/register', { data: { firstName, lastName: 'Iyer', email, phoneNumber: '9876543210', password } }));
    const token = (await data<{ accessToken: string }>(await this.request.post('/api/v1/auth/login', { data: { email, password } }))).accessToken;
    return { email, password, token };
  }

  /** A customer account, signed in on this page (for tests that aren't about registering). */
  async signedInCustomer(page: Page, firstName = 'Asha'): Promise<string> {
    const email = `${firstName.toLowerCase()}${Date.now()}@example.com`;
    await data(
      await this.request.post('/api/v1/auth/register', {
        data: { firstName, lastName: 'Rao', email, phoneNumber: '9876543210', password: 'Password@123' },
      }),
    );
    const token = (await data<{ accessToken: string }>(await this.request.post('/api/v1/auth/login', { data: { email, password: 'Password@123' } })))
      .accessToken;
    await page.addInitScript((t) => window.localStorage.setItem('cartwheel.token', t), token);
    return email;
  }
}

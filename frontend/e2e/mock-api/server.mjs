// An in-memory stand-in for the api-gateway and the services behind it, for the storefront's browser tests and for
// working on the UI without the backend. Same paths, envelopes, status codes and rules as the real services (the
// parts the storefront uses), including the delays of Kafka-driven steps. Not used by the real app.
//
//   node e2e/mock-api/server.mjs            (port 4010, or MOCK_API_PORT)
//   POST /__mock/reset                       back to the seed data

import http from 'node:http';
import { randomUUID } from 'node:crypto';

const PORT = Number(process.env.MOCK_API_PORT || 4010);
const EVENT_DELAY = Number(process.env.MOCK_EVENT_DELAY_MS || 400); // how long "Kafka" takes
const SHIPPING_FEE = 40;
const FREE_SHIPPING_FROM = 499;
const PAYMENT_WINDOW_MS = 30 * 60_000;

let db;

// ---------------------------------------------------------------- seed

function seed() {
  db = {
    users: new Map(),
    categories: [],
    products: new Map(),
    stock: new Map(),
    reviews: [],
    votes: new Set(),
    carts: new Map(),
    wishlists: new Map(),
    orders: new Map(),
    payments: new Map(),
    shipments: new Map(),
    views: new Map(), // userId -> [productId] newest first
    searches: new Map(),
    seq: 1000,
    movements: [], // stock movements
    activity: [], // the admin dashboard's feed
    emails: [], // what notification-service would have sent
  };
  addUser({ firstName: 'Shop', lastName: 'Admin', email: 'admin@ecommerce.com', phoneNumber: '9000000000', password: 'Admin@12345' }, 'ADMIN');
  const reviewer = addUser({ firstName: 'Priya', lastName: 'Sharma', email: 'priya@example.com', phoneNumber: '9876543211', password: 'Password@123' });

  const electronics = addCategory('Electronics', null);
  const phones = addCategory('Phones', electronics.id);
  const audio = addCategory('Audio', electronics.id);
  const home = addCategory('Home & Kitchen', null);
  const lighting = addCategory('Lighting', home.id);
  const kitchen = addCategory('Kitchen', home.id);
  const fashion = addCategory('Fashion', null);

  const items = [
    ['PIX-9', 'Pixel 9', 'Google', phones, 79999, 10, 'A 6.3-inch phone with a camera that’s good in low light and seven years of updates.', 12],
    ['GAL-S24', 'Galaxy S24', 'Samsung', phones, 74999, 5, 'Compact flagship with a bright 120 Hz screen.', 8],
    ['NORD-4', 'Nord 4', 'OnePlus', phones, 29999, 0, 'Metal body, fast charging, clean software.', 20],
    ['CASE-P9', 'Pixel 9 Case', 'Spigen', phones, 999, 20, 'Slim, grippy case with raised edges for the camera.', 50],
    ['BUDS-2', 'Pixel Buds Pro 2', 'Google', audio, 22900, 15, 'Noise-cancelling earbuds that fit small ears.', 14],
    ['SPK-FLIP', 'Flip 6 Speaker', 'JBL', audio, 11999, 25, 'Waterproof portable speaker, 12 hours of play.', 9],
    ['HP-XM5', 'WH-1000XM5 Headphones', 'Sony', audio, 34990, 20, 'Over-ear headphones with excellent noise cancelling.', 0],
    ['LAMP-LED', 'Desk Lamp', 'Philips', lighting, 1499, 0, 'LED desk lamp with three colour temperatures and a USB port.', 6],
    ['BULB-SM', 'Smart Bulb', 'Wipro', lighting, 649, 15, '9 W colour bulb you control from your phone.', 40],
    ['KETTLE', 'Electric Kettle 1.5 L', 'Prestige', kitchen, 1295, 20, 'Stainless steel kettle with auto shut-off.', 18],
    ['PAN-28', 'Non-stick Pan 28 cm', 'Hawkins', kitchen, 1890, 0, 'Induction-ready pan with a hard-anodised base.', 11],
    ['TEE-CTN', 'Cotton T-shirt', 'Bewakoof', fashion, 499, 30, 'Soft, breathable everyday tee.', 60],
  ];
  for (const [sku, name, brand, cat, price, discount, description, qty] of items) {
    const p = addProduct({ sku, name, brand, categoryId: cat.id, price, discountPercent: discount, description, imageUrls: [] });
    setStock(p.id, qty);
  }
  const pixel = [...db.products.values()].find((p) => p.sku === 'PIX-9');
  addReview(reviewer, pixel.id, 5, 'Best camera I’ve had', 'Night photos are excellent. Battery lasts a full day.', true);
  db.searches.set('pixel', 12);
  db.searches.set('earbuds', 7);
  db.searches.set('desk lamp', 5);
}

function addUser(r, role = 'CUSTOMER') {
  const user = { userId: randomUUID(), firstName: r.firstName, lastName: r.lastName, email: r.email.toLowerCase(), phoneNumber: r.phoneNumber, password: r.password, role, accountStatus: 'ACTIVE', createdAt: now(), updatedAt: now() };
  db.users.set(user.userId, user);
  return user;
}

function addCategory(name, parentId, description = null) {
  const c = { id: randomUUID(), name, description, parentId };
  db.categories.push(c);
  return c;
}

function addProduct(r) {
  const p = {
    id: randomUUID(), sku: r.sku, name: r.name, description: r.description ?? null, brand: r.brand ?? null, categoryId: r.categoryId ?? null,
    price: Number(r.price), discountPercent: Number(r.discountPercent ?? 0), currency: 'INR', status: 'ACTIVE', imageUrls: r.imageUrls ?? [],
    createdAt: now(), updatedAt: now(),
  };
  db.products.set(p.id, p);
  db.stock.set(p.id, { onHand: 0, reserved: 0, threshold: 10 });
  return p;
}

function setStock(productId, onHand) {
  const s = db.stock.get(productId);
  s.onHand = onHand;
  s.alerted = available(productId) <= s.threshold;
}

function addReview(user, productId, rating, title, comment, verified) {
  const r = { id: randomUUID(), productId, userId: user.userId, reviewerName: `${user.firstName} ${user.lastName[0]}.`, rating, title, comment, verifiedPurchase: verified, helpfulCount: 0, status: 'VISIBLE', createdAt: now(), editedAt: null };
  db.reviews.push(r);
  return r;
}

// ---------------------------------------------------------------- helpers

const now = () => new Date().toISOString();
const round2 = (n) => Math.round(n * 100) / 100;
const sellingPrice = (p) => round2(p.price * (1 - p.discountPercent / 100));
const available = (id) => {
  const s = db.stock.get(id);
  return s ? Math.max(0, s.onHand - s.reserved) : 0;
};
const category = (id) => db.categories.find((c) => c.id === id) ?? null;
const descendants = (id) => {
  const out = [id];
  for (const c of db.categories) if (c.parentId === id) out.push(...descendants(c.id));
  return out;
};
const rating = (productId) => {
  const visible = db.reviews.filter((r) => r.productId === productId && r.status === 'VISIBLE');
  const avg = visible.length ? Math.round((visible.reduce((s, r) => s + r.rating, 0) / visible.length) * 10) / 10 : 0;
  return { averageRating: avg, reviewCount: visible.length, visible };
};
const later = (fn) => setTimeout(fn, EVENT_DELAY);

function productView(p) {
  const c = category(p.categoryId);
  const r = rating(p.id);
  return {
    id: p.id, sku: p.sku, name: p.name, description: p.description, brand: p.brand, category: c ? { id: c.id, name: c.name } : null,
    price: p.price, discountPercent: p.discountPercent, sellingPrice: sellingPrice(p), currency: 'INR', status: p.status,
    availableQuantity: available(p.id), inStock: available(p.id) > 0, averageRating: r.averageRating, reviewCount: r.reviewCount,
    imageUrls: p.imageUrls, createdAt: p.createdAt, updatedAt: p.updatedAt,
  };
}

function hit(p) {
  const v = productView(p);
  return { productId: p.id, sku: p.sku, name: p.name, brand: p.brand ?? undefined, imageUrl: p.imageUrls[0], price: p.price, sellingPrice: v.sellingPrice, discountPercent: p.discountPercent, currency: 'INR', averageRating: v.averageRating, reviewCount: v.reviewCount, inStock: v.inStock, availableQuantity: v.availableQuantity, categoryId: p.categoryId ?? undefined, categoryName: category(p.categoryId)?.name };
}

function recommended(p, reason) {
  const h = hit(p);
  return { productId: h.productId, name: h.name, brand: h.brand, imageUrl: h.imageUrl, sellingPrice: h.sellingPrice, currency: 'INR', averageRating: h.averageRating, reviewCount: h.reviewCount, inStock: h.inStock, categoryId: h.categoryId, categoryName: h.categoryName, reason };
}

function page(content, pageNo, size) {
  const total = content.length;
  const slice = content.slice(pageNo * size, pageNo * size + size);
  const totalPages = Math.ceil(total / size);
  return { content: slice, page: pageNo, size, totalElements: total, totalPages, first: pageNo === 0, last: pageNo >= totalPages - 1 };
}

function token(user) {
  const enc = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
  return `${enc({ alg: 'HS256' })}.${enc({ sub: user.userId, email: user.email, role: user.role, iss: 'ecommerce-user-service', exp: Math.floor(Date.now() / 1000) + 3600 })}.mock`;
}

function caller(req) {
  const header = req.headers.authorization ?? '';
  if (!header.startsWith('Bearer ')) return null;
  try {
    const claims = JSON.parse(Buffer.from(header.slice(7).split('.')[1], 'base64url').toString());
    if (claims.exp * 1000 < Date.now()) return null;
    const u = db.users.get(claims.sub);
    return u && u.accountStatus === 'ACTIVE' ? u : null;
  } catch {
    return null;
  }
}

class Http extends Error {
  constructor(status, message, validationErrors) {
    super(message);
    this.status = status;
    this.validationErrors = validationErrors;
  }
}

const ok = (data, status = 200, message = 'OK') => ({ status, body: { success: true, message, data } });
const nothing = () => ({ status: 204 });

function invalid(errors) {
  if (Object.keys(errors).length) throw new Http(400, 'Validation failed', errors);
}

// ---------------------------------------------------------------- cart, orders, payments, shipping

function cartOf(userId) {
  if (!db.carts.has(userId)) db.carts.set(userId, []);
  return db.carts.get(userId);
}

function cartView(userId) {
  const lines = cartOf(userId);
  const items = lines.map((l) => {
    const p = db.products.get(l.productId);
    const avail = available(l.productId);
    const price = sellingPrice(p);
    let status = 'AVAILABLE';
    let message = null;
    if (p.status !== 'ACTIVE') [status, message] = ['UNAVAILABLE', `${p.name} is no longer available`];
    else if (avail === 0) [status, message] = ['OUT_OF_STOCK', `${p.name} is out of stock`];
    else if (avail < l.quantity) [status, message] = ['INSUFFICIENT_STOCK', `Only ${avail} units of ${p.name} are available`];
    return { productId: p.id, sku: p.sku, name: p.name, imageUrl: p.imageUrls[0] ?? null, quantity: l.quantity, unitPrice: price, listPrice: p.discountPercent > 0 ? p.price : null, priceWhenAdded: l.priceWhenAdded, priceChanged: l.priceWhenAdded !== price, lineTotal: round2(price * l.quantity), availableQuantity: avail, status, message, addedAt: l.addedAt };
  });
  const subtotal = round2(items.reduce((s, i) => s + i.lineTotal, 0));
  const savings = round2(items.reduce((s, i) => s + (i.listPrice ? (i.listPrice - i.unitPrice) * i.quantity : 0), 0));
  return { items, totalQuantity: items.reduce((s, i) => s + i.quantity, 0), subtotal, totalSavings: savings, currency: 'INR', checkoutReady: items.length > 0 && items.every((i) => i.status === 'AVAILABLE'), pricesUpToDate: true, warnings: [] };
}

function orderView(o) {
  return { ...o, timeline: o.timeline.map((t) => ({ ...t, changedBy: null })) };
}

function moveOrder(o, to, note = null) {
  o.timeline.push({ fromStatus: o.status, toStatus: to, note, changedAt: now() });
  o.status = to;
  if (to === 'CONFIRMED') o.confirmedAt = now();
  if (to === 'SHIPPED') o.shippedAt = now();
  if (to === 'DELIVERED') o.deliveredAt = now();
  if (to === 'CANCELLED') {
    o.cancelledAt = now();
    o.statusReason = note;
  }
}

function estimateDays(pin) {
  const origin = '560001';
  let shared = 0;
  while (shared < 3 && pin[shared] === origin[shared]) shared++;
  return [5, 3, 2, 1][shared];
}

function addDays(days) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

const stockView = (id) => {
  const s = db.stock.get(id);
  const p = db.products.get(id);
  return { productId: id, sku: p.sku, productName: p.name, productStatus: p.status, quantityOnHand: s.onHand, quantityReserved: s.reserved, availableQuantity: available(id), lowStockThreshold: s.threshold, lowStock: available(id) <= s.threshold, updatedAt: s.updatedAt ?? p.updatedAt };
};

function checkProduct(b) {
  const e = {};
  if (!/^[A-Za-z0-9-]{3,64}$/.test(b.sku ?? '')) e.sku = 'SKU must be 3-64 letters, digits or hyphens';
  if (!b.name || b.name.trim().length < 2 || b.name.length > 200) e.name = 'Name must be 2-200 characters';
  if (!(Number(b.price) > 0)) e.price = 'Price must be greater than 0';
  if (b.discountPercent != null && !(Number(b.discountPercent) >= 0 && Number(b.discountPercent) <= 99.99)) e.discountPercent = 'Discount must be between 0 and 99.99';
  if (b.categoryId && !category(b.categoryId)) e.categoryId = 'Category not found';
  if ((b.imageUrls ?? []).length > 10) e.imageUrls = 'At most 10 images';
  invalid(e);
}

// ---------- what the other services would record (stock movements, dashboard feed, emails) ----------

const IST = 5.5 * 3600_000;
const istDay = (iso) => new Date(Date.parse(iso) + IST).toISOString().slice(0, 10);
const dayStart = (day) => Date.parse(`${day}T00:00:00+05:30`);

function log(kind, severity, message, orderNumber, productId) {
  db.activity.unshift({ occurredAt: now(), kind, severity, message, orderNumber: orderNumber ?? undefined, productId: productId ?? undefined });
}

function email(o, eventType, subject) {
  const u = db.users.get(o.userId);
  db.emails.unshift({ id: randomUUID(), eventType, orderNumber: o.orderNumber, userId: o.userId, channel: 'EMAIL', recipient: u?.email ?? '', subject, status: 'SENT', sentAt: now() });
}

const inr = (n) => `INR ${Number(n).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

/** Changes stock the way inventory-service does: records a movement, raises a low-stock alert once. */
function changeStock(productId, type, change, opts = {}) {
  const s = db.stock.get(productId);
  if (type === 'RESERVE') s.reserved += change;
  else if (type === 'RELEASE') s.reserved -= change;
  else if (type === 'SALE') {
    s.reserved -= change;
    s.onHand -= change;
  } else s.onHand += change; // ADJUSTMENT, RESTOCK, RETURN
  s.updatedAt = now();
  db.movements.unshift({ id: randomUUID(), productId, movementType: type, quantityChange: type === 'RESERVE' || type === 'SALE' ? -change : change, quantityOnHandAfter: s.onHand, quantityReservedAfter: s.reserved, reason: opts.reason ?? null, reference: opts.reference ?? null, createdAt: now() });
  checkLow(productId);
}

function checkLow(productId) {
  const s = db.stock.get(productId);
  const p = db.products.get(productId);
  const low = available(productId) <= s.threshold;
  if (low && !s.alerted) {
    s.alerted = true;
    log('LOW_STOCK', 'WARNING', `Low stock: ${p.name} (${p.sku}) - ${available(productId)} left, threshold ${s.threshold}`, null, productId);
  } else if (!low) s.alerted = false;
}

function track(s, status, description, location) {
  s.status = status;
  s.tracking.push({ status, description, location, occurredAt: now() });
}

// ---------------------------------------------------------------- routes

const routes = [];
const route = (method, pattern, handler, access = 'public') => {
  const keys = [];
  const regex = new RegExp('^' + pattern.replace(/:(\w+)/g, (_, k) => (keys.push(k), '([^/]+)')) + '$');
  routes.push({ method, regex, keys, handler, access });
};

// auth & users
route('POST', '/api/v1/auth/register', ({ body }) => {
  const e = {};
  if (!body.firstName?.trim()) e.firstName = 'First name is required';
  if (!body.lastName?.trim()) e.lastName = 'Last name is required';
  if (!/^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(body.email ?? '')) e.email = 'Email must be a valid email address';
  if (!/^[0-9]{10,15}$/.test(body.phoneNumber ?? '')) e.phoneNumber = 'Phone number must contain 10 to 15 digits';
  if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9\s])\S{8,64}$/.test(body.password ?? '')) e.password = 'Password is too weak';
  invalid(e);
  if ([...db.users.values()].some((u) => u.email === body.email.toLowerCase())) throw new Http(409, 'An account with this email already exists');
  const u = addUser(body);
  return ok({ userId: u.userId, email: u.email, role: u.role }, 201, 'User registered successfully');
});
route('POST', '/api/v1/auth/login', ({ body }) => {
  const u = [...db.users.values()].find((x) => x.email === (body.email ?? '').toLowerCase() && x.password === body.password);
  if (!u) throw new Http(401, 'Invalid email or password');
  if (u.accountStatus !== 'ACTIVE') throw new Http(403, `Your account is ${u.accountStatus.toLowerCase()}. Contact support.`);
  return ok({ accessToken: token(u), tokenType: 'Bearer', user: { userId: u.userId, email: u.email, role: u.role } });
});
const profile = (u) => ({ userId: u.userId, firstName: u.firstName, lastName: u.lastName, email: u.email, phoneNumber: u.phoneNumber, role: u.role, accountStatus: u.accountStatus, createdAt: u.createdAt, updatedAt: u.updatedAt });
route('GET', '/api/v1/users/me', ({ user }) => ok(profile(user)), 'user');
route('PUT', '/api/v1/users/me', ({ user, body }) => {
  const e = {};
  if (!body.firstName?.trim()) e.firstName = 'First name is required';
  if (!body.lastName?.trim()) e.lastName = 'Last name is required';
  if (!/^[0-9]{10,15}$/.test(body.phoneNumber ?? '')) e.phoneNumber = 'Phone number must contain 10 to 15 digits';
  invalid(e);
  Object.assign(user, { firstName: body.firstName.trim(), lastName: body.lastName.trim(), phoneNumber: body.phoneNumber, updatedAt: now() });
  return ok(profile(user));
}, 'user');

// catalog
route('GET', '/api/v1/categories', () => {
  const tree = (parentId) => db.categories.filter((c) => c.parentId === parentId).map((c) => ({ id: c.id, name: c.name, description: c.description, children: tree(c.id) }));
  return ok(tree(null));
});
route('GET', '/api/v1/products/:id', ({ params, user }) => {
  const p = db.products.get(params.id);
  if (!p || (p.status !== 'ACTIVE' && user?.role !== 'ADMIN')) throw new Http(404, `Product not found: ${params.id}`);
  return ok(productView(p));
});
route('POST', '/api/v1/products', ({ body }) => {
  if ([...db.products.values()].some((p) => p.sku === body.sku)) throw new Http(409, `A product with SKU ${body.sku} already exists`);
  checkProduct(body);
  const p = addProduct(body);
  p.status = body.status ?? 'ACTIVE';
  return ok(productView(p), 201);
}, 'admin');
route('GET', '/api/v1/inventory/:id', ({ params }) => {
  const s = db.stock.get(params.id);
  if (!s) throw new Http(404, 'Inventory item not found');
  return ok(stockView(params.id));
}, 'admin');
route('PUT', '/api/v1/inventory/:id/stock', ({ params, body }) => {
  const s = db.stock.get(params.id);
  if (!s) throw new Http(404, 'Inventory item not found');
  const target = Number(body.quantityOnHand);
  if (!(target >= 0)) invalid({ quantityOnHand: 'quantityOnHand cannot be negative' });
  if (target < s.reserved) throw new Http(409, `${s.reserved} units are reserved for orders; on hand can't go below that`);
  if (target !== s.onHand) changeStock(params.id, target > s.onHand ? 'RESTOCK' : 'ADJUSTMENT', target - s.onHand, { reason: body.reason ?? 'Stock count' });
  return ok(stockView(params.id));
}, 'admin');

// search
function matches(p, q) {
  const text = `${p.name} ${p.brand ?? ''} ${p.sku} ${category(p.categoryId)?.name ?? ''} ${p.description ?? ''}`.toLowerCase();
  const words = text.split(/[^a-z0-9]+/).filter(Boolean);
  return q
    .toLowerCase()
    .split(/\s+/)
    .filter(Boolean)
    .every((w) => words.some((x) => x.startsWith(w) || x.replace(/s$/, '') === w.replace(/s$/, '')));
}
function distance(a, b) {
  const d = Array.from({ length: a.length + 1 }, (_, i) => [i, ...Array(b.length).fill(0)]);
  for (let j = 1; j <= b.length; j++) d[0][j] = j;
  for (let i = 1; i <= a.length; i++)
    for (let j = 1; j <= b.length; j++) {
      d[i][j] = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
      if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
    }
  return d[a.length][b.length];
}
function correct(q) {
  const dictionary = new Set();
  for (const p of db.products.values()) `${p.name} ${p.brand ?? ''}`.toLowerCase().split(/[^a-z0-9]+/).filter((w) => w.length > 2).forEach((w) => dictionary.add(w));
  let changed = false;
  const words = q.toLowerCase().split(/\s+/).map((w) => {
    if (/^\d+$/.test(w) || dictionary.has(w)) return w;
    let best = null;
    for (const d of dictionary) {
      const dist = distance(w, d);
      if (dist <= (w.length > 5 ? 2 : 1) && (!best || dist < best[1])) best = [d, dist];
    }
    if (best) changed = true;
    return best ? best[0] : w;
  });
  return changed ? words.join(' ') : null;
}

route('GET', '/api/v1/search/products', ({ query }) => {
  const q = (query.get('q') ?? '').trim();
  const brands = query.getAll('brand');
  const categoryId = query.get('categoryId');
  const minPrice = query.get('minPrice') ? Number(query.get('minPrice')) : null;
  const maxPrice = query.get('maxPrice') ? Number(query.get('maxPrice')) : null;
  const minRating = query.get('minRating') ? Number(query.get('minRating')) : null;
  const inStock = query.get('inStock') === 'true';
  const sort = query.get('sort') ?? 'relevance';
  const pageNo = Number(query.get('page') ?? 0);
  const size = Math.min(50, Number(query.get('size') ?? 20));

  const active = [...db.products.values()].filter((p) => p.status === 'ACTIVE');
  let text = q;
  let corrected = null;
  let base = q ? active.filter((p) => matches(p, q)) : active;
  if (q && base.length === 0) {
    corrected = correct(q);
    if (corrected) {
      text = corrected;
      base = active.filter((p) => matches(p, corrected));
    }
  }
  if (q && base.length) db.searches.set(text.toLowerCase(), (db.searches.get(text.toLowerCase()) ?? 0) + 1);

  const inCategory = (p) => !categoryId || descendants(categoryId).includes(p.categoryId);
  const byBrand = (p) => !brands.length || brands.includes(p.brand);
  const byPrice = (p) => (minPrice === null || sellingPrice(p) >= minPrice) && (maxPrice === null || sellingPrice(p) < maxPrice);
  const byRating = (p) => minRating === null || rating(p.id).averageRating >= minRating;
  const byStock = (p) => !inStock || available(p.id) > 0;
  const all = [inCategory, byBrand, byPrice, byRating, byStock];
  const except = (skip) => base.filter((p) => all.every((f) => f === skip || f(p)));

  let results = except(null);
  const sorters = {
    price_asc: (a, b) => sellingPrice(a) - sellingPrice(b),
    price_desc: (a, b) => sellingPrice(b) - sellingPrice(a),
    rating: (a, b) => rating(b.id).averageRating - rating(a.id).averageRating,
    newest: (a, b) => b.createdAt.localeCompare(a.createdAt),
    relevance: (a, b) => (available(b.id) > 0) - (available(a.id) > 0),
  };
  results = [...results].sort(sorters[sort] ?? sorters.relevance);

  const count = (list, key) => {
    const m = new Map();
    list.forEach((p) => {
      const k = key(p);
      if (k) m.set(k, (m.get(k) ?? 0) + 1);
    });
    return m;
  };
  const brandCounts = count(except(byBrand), (p) => p.brand);
  const catCounts = count(except(inCategory), (p) => p.categoryId);
  const priceBase = except(byPrice);
  const ranges = [[0, 500, 'Under ₹500'], [500, 1000, '₹500 – ₹1,000'], [1000, 5000, '₹1,000 – ₹5,000'], [5000, 20000, '₹5,000 – ₹20,000'], [20000, null, '₹20,000 & above']];
  const ratingBase = except(byRating);
  const stockBase = except(byStock);
  return ok({
    query: q || undefined,
    correctedQuery: corrected ?? undefined,
    results: page(results.map(hit), pageNo, size),
    facets: {
      brands: [...brandCounts].map(([value, n]) => ({ value, count: n })).sort((a, b) => b.count - a.count),
      categories: [...catCounts].map(([id, n]) => ({ id, name: category(id).name, count: n })),
      priceRanges: ranges.map(([min, max, label]) => ({ label, min, max: max ?? undefined, count: priceBase.filter((p) => sellingPrice(p) >= min && (max === null || sellingPrice(p) < max)).length })),
      ratings: [4, 3, 2, 1].map((r) => ({ minRating: r, label: `${r} stars & up`, count: ratingBase.filter((p) => rating(p.id).averageRating >= r).length })),
      availability: { inStock: stockBase.filter((p) => available(p.id) > 0).length, outOfStock: stockBase.filter((p) => available(p.id) === 0).length },
    },
  });
});
route('GET', '/api/v1/search/suggest', ({ query }) => {
  const q = (query.get('q') ?? '').trim().toLowerCase();
  const popular = [...db.searches].sort((a, b) => b[1] - a[1]).map(([t]) => t);
  if (!q) return ok({ query: '', products: [], brands: [], categories: [], searches: popular.slice(0, 5) });
  const active = [...db.products.values()].filter((p) => p.status === 'ACTIVE' && matches(p, q));
  return ok({
    query: q,
    products: active.slice(0, 5).map((p) => ({ productId: p.id, name: p.name, brand: p.brand, imageUrl: p.imageUrls[0] ?? null, sellingPrice: sellingPrice(p) })),
    brands: [...new Set(active.map((p) => p.brand).filter((b) => b?.toLowerCase().startsWith(q)))],
    categories: db.categories.filter((c) => c.name.toLowerCase().startsWith(q)).map((c) => ({ id: c.id, name: c.name })),
    searches: popular.filter((t) => t.startsWith(q)).slice(0, 5),
  });
});
route('GET', '/api/v1/search/popular', ({ query }) => ok([...db.searches].sort((a, b) => b[1] - a[1]).slice(0, Number(query.get('limit') ?? 10)).map(([term, count]) => ({ term, count }))));

// recommendations
const paidOrders = () => [...db.orders.values()].filter((o) => ['CONFIRMED', 'SHIPPED', 'DELIVERED'].includes(o.status));
const buyable = (p) => p && p.status === 'ACTIVE' && available(p.id) > 0;
const limitOf = (query) => Math.min(20, Number(query.get('limit') ?? 8));
route('GET', '/api/v1/recommendations/trending', ({ query }) => {
  const sold = new Map();
  paidOrders().forEach((o) => o.items.forEach((i) => sold.set(i.productId, (sold.get(i.productId) ?? 0) + i.quantity)));
  const categoryId = query.get('categoryId');
  const pool = [...db.products.values()].filter((p) => buyable(p) && (!categoryId || descendants(categoryId).includes(p.categoryId)));
  pool.sort((a, b) => (sold.get(b.id) ?? 0) - (sold.get(a.id) ?? 0) || rating(b.id).averageRating - rating(a.id).averageRating);
  const name = categoryId ? category(categoryId)?.name : null;
  return ok({ title: name ? `Trending in ${name}` : 'Trending now', products: pool.slice(0, limitOf(query)).map((p) => recommended(p, sold.get(p.id) ? `${sold.get(p.id)} sold in the last 7 days` : 'Top rated')) });
});
route('GET', '/api/v1/recommendations/products/:id/:kind', ({ params, query }) => {
  const p = db.products.get(params.id);
  if (!p) throw new Http(404, 'Product not found');
  const limit = limitOf(query);
  if (params.kind === 'similar') {
    const list = [...db.products.values()].filter((x) => x.id !== p.id && buyable(x) && x.categoryId === p.categoryId);
    return ok({ title: 'Similar products', products: list.slice(0, limit).map((x) => recommended(x, `Also in ${category(x.categoryId)?.name}`)) });
  }
  if (params.kind === 'bought-together') {
    const counts = new Map();
    paidOrders().filter((o) => o.items.some((i) => i.productId === p.id)).forEach((o) => o.items.forEach((i) => i.productId !== p.id && counts.set(i.productId, (counts.get(i.productId) ?? 0) + 1)));
    const list = [...counts].sort((a, b) => b[1] - a[1]).map(([id, n]) => [db.products.get(id), n]).filter(([x]) => buyable(x));
    return ok({ title: 'Frequently bought together', products: list.slice(0, limit).map(([x, n]) => recommended(x, `Bought together in ${n} order${n > 1 ? 's' : ''}`)) });
  }
  const counts = new Map();
  for (const viewed of db.views.values()) if (viewed.includes(p.id)) viewed.filter((id) => id !== p.id).forEach((id) => counts.set(id, (counts.get(id) ?? 0) + 1));
  const list = [...counts].sort((a, b) => b[1] - a[1]).map(([id, n]) => [db.products.get(id), n]).filter(([x]) => buyable(x));
  return ok({ title: 'Customers who viewed this also viewed', products: list.slice(0, limit).map(([x, n]) => recommended(x, `Viewed by ${n} customer${n > 1 ? 's' : ''} who viewed this`)) });
});
route('POST', '/api/v1/recommendations/views', ({ user, body }) => {
  const list = (db.views.get(user.userId) ?? []).filter((id) => id !== body.productId);
  db.views.set(user.userId, [body.productId, ...list].slice(0, 200));
  return nothing();
}, 'user');
route('GET', '/api/v1/recommendations/recently-viewed', ({ user, query }) => ok({ title: 'Recently viewed', products: (db.views.get(user.userId) ?? []).map((id) => db.products.get(id)).filter((p) => p?.status === 'ACTIVE').slice(0, limitOf(query)).map((p) => recommended(p, undefined)) }), 'user');
route('DELETE', '/api/v1/recommendations/recently-viewed', ({ user }) => (db.views.delete(user.userId), nothing()), 'user');
route('GET', '/api/v1/recommendations/for-you', ({ user, query }) => {
  const bought = paidOrders().filter((o) => o.userId === user.userId).flatMap((o) => o.items.map((i) => i.productId));
  const viewed = db.views.get(user.userId) ?? [];
  const seeds = [...new Set([...bought, ...viewed])].map((id) => db.products.get(id)).filter(Boolean);
  const exclude = new Set([...bought, ...viewed.slice(0, 20)]);
  const picks = [];
  for (const s of seeds) for (const p of db.products.values()) if (buyable(p) && !exclude.has(p.id) && p.categoryId === s.categoryId && !picks.some((x) => x.productId === p.id)) picks.push(recommended(p, `Similar to ${s.name}`));
  for (const p of db.products.values()) if (picks.length < 8 && buyable(p) && !exclude.has(p.id) && !picks.some((x) => x.productId === p.id)) picks.push(recommended(p, 'Popular right now'));
  return ok({ title: 'Recommended for you', products: picks.slice(0, limitOf(query)) });
}, 'user');

// reviews
const reviewView = (r, own = false) => ({ id: r.id, productId: r.productId, reviewerName: r.reviewerName, rating: r.rating, title: r.title, comment: r.comment ?? undefined, verifiedPurchase: r.verifiedPurchase, helpfulCount: r.helpfulCount, status: r.status, hiddenReason: own ? r.hiddenReason : undefined, createdAt: r.createdAt, editedAt: r.editedAt ?? undefined });
const hasReceived = (userId, productId) => [...db.orders.values()].some((o) => o.userId === userId && o.status === 'DELIVERED' && o.items.some((i) => i.productId === productId));
function checkReview(body) {
  const e = {};
  if (!(body.rating >= 1 && body.rating <= 5)) e.rating = 'rating must be between 1 and 5';
  if (!body.title?.trim()) e.title = 'title is required';
  invalid(e);
}
route('GET', '/api/v1/reviews/products/:id', ({ params, query }) => {
  let list = rating(params.id).visible;
  if (query.get('rating')) list = list.filter((r) => r.rating === Number(query.get('rating')));
  if (query.get('verifiedOnly') === 'true') list = list.filter((r) => r.verifiedPurchase);
  const [field, dir] = (query.get('sort') ?? 'createdAt,desc').split(',');
  list = [...list].sort((a, b) => (a[field] > b[field] ? 1 : a[field] < b[field] ? -1 : 0) * (dir === 'asc' ? 1 : -1));
  return ok(page(list.map((r) => reviewView(r)), Number(query.get('page') ?? 0), Number(query.get('size') ?? 10)));
});
route('GET', '/api/v1/reviews/products/:id/summary', ({ params }) => {
  const r = rating(params.id);
  const counts = {};
  for (let s = 5; s >= 1; s--) counts[s] = r.visible.filter((x) => x.rating === s).length;
  return ok({ productId: params.id, averageRating: r.averageRating, reviewCount: r.reviewCount, ratingCounts: counts });
});
route('POST', '/api/v1/reviews/products/:id', ({ user, params, body }) => {
  if (!db.products.get(params.id)) throw new Http(404, 'Product not found');
  checkReview(body);
  if (db.reviews.some((r) => r.productId === params.id && r.userId === user.userId)) throw new Http(409, 'You have already reviewed this product. Edit your review instead.');
  return ok(reviewView(addReview(user, params.id, body.rating, body.title.trim(), body.comment?.trim() || null, hasReceived(user.userId, params.id)), true), 201);
}, 'user');
route('GET', '/api/v1/reviews/me', ({ user }) => ok(page(db.reviews.filter((r) => r.userId === user.userId).map((r) => reviewView(r, true)), 0, 10)), 'user');
route('PUT', '/api/v1/reviews/:id', ({ user, params, body }) => {
  const r = db.reviews.find((x) => x.id === params.id && x.userId === user.userId);
  if (!r) throw new Http(404, 'Review not found');
  checkReview(body);
  Object.assign(r, { rating: body.rating, title: body.title.trim(), comment: body.comment?.trim() || null, editedAt: now() });
  return ok(reviewView(r, true));
}, 'user');
route('DELETE', '/api/v1/reviews/:id', ({ user, params }) => {
  const i = db.reviews.findIndex((x) => x.id === params.id && (x.userId === user.userId || user.role === 'ADMIN'));
  if (i < 0) throw new Http(404, 'Review not found');
  db.reviews.splice(i, 1);
  return nothing();
}, 'user');
route('POST', '/api/v1/reviews/:id/helpful', ({ user, params }) => {
  const r = db.reviews.find((x) => x.id === params.id && x.status === 'VISIBLE');
  if (!r) throw new Http(404, 'Review not found');
  if (r.userId === user.userId) throw new Http(400, "You can't mark your own review as helpful");
  const key = `${r.id}:${user.userId}`;
  if (!db.votes.has(key)) (db.votes.add(key), r.helpfulCount++);
  return ok({ reviewId: r.id, helpfulCount: r.helpfulCount, votedByMe: true });
}, 'user');
route('DELETE', '/api/v1/reviews/:id/helpful', ({ user, params }) => {
  const r = db.reviews.find((x) => x.id === params.id);
  if (!r) throw new Http(404, 'Review not found');
  if (db.votes.delete(`${r.id}:${user.userId}`)) r.helpfulCount--;
  return ok({ reviewId: r.id, helpfulCount: r.helpfulCount, votedByMe: false });
}, 'user');

// cart
route('GET', '/api/v1/cart', ({ user }) => ok(cartView(user.userId)), 'user');
route('POST', '/api/v1/cart/items', ({ user, body }) => {
  const p = db.products.get(body.productId);
  if (!p || p.status !== 'ACTIVE') throw new Http(404, `Product not found or not available for sale: ${body.productId}`);
  const avail = available(p.id);
  if (avail <= 0) throw new Http(409, `${p.name} is out of stock`);
  const lines = cartOf(user.userId);
  const line = lines.find((l) => l.productId === p.id);
  const qty = (line?.quantity ?? 0) + Number(body.quantity);
  if (qty > Math.min(10, avail)) throw new Http(409, `Only ${avail} units of ${p.name} are available${line ? ` (you already have ${line.quantity} in your cart)` : ''}`);
  if (line) line.quantity = qty;
  else lines.push({ productId: p.id, quantity: qty, priceWhenAdded: sellingPrice(p), addedAt: now() });
  return ok(cartView(user.userId));
}, 'user');
route('PUT', '/api/v1/cart/items/:pid', ({ user, params, body }) => {
  const line = cartOf(user.userId).find((l) => l.productId === params.pid);
  if (!line) throw new Http(404, `Product ${params.pid} is not in your cart`);
  if (body.quantity > available(params.pid)) throw new Http(409, `Only ${available(params.pid)} units of ${db.products.get(params.pid).name} are available`);
  line.quantity = Number(body.quantity);
  return ok(cartView(user.userId));
}, 'user');
route('DELETE', '/api/v1/cart/items/:pid', ({ user, params }) => {
  db.carts.set(user.userId, cartOf(user.userId).filter((l) => l.productId !== params.pid));
  return ok(cartView(user.userId));
}, 'user');

// orders
route('POST', '/api/v1/orders/checkout', ({ user, body, headers }) => {
  const key = headers['idempotency-key'];
  if (!key) throw new Http(400, 'The Idempotency-Key header is required');
  const existing = [...db.orders.values()].find((o) => o.userId === user.userId && o.idempotencyKey === key);
  if (existing) return ok(orderView(existing));
  const a = body.shippingAddress ?? {};
  const e = {};
  if (!a.fullName?.trim()) e['shippingAddress.fullName'] = 'fullName is required';
  if (!/^[6-9][0-9]{9}$/.test(a.phone ?? '')) e['shippingAddress.phone'] = 'phone must be a 10-digit Indian mobile number';
  if (!a.line1?.trim()) e['shippingAddress.line1'] = 'line1 is required';
  if (!a.city?.trim()) e['shippingAddress.city'] = 'city is required';
  if (!a.state?.trim()) e['shippingAddress.state'] = 'state is required';
  if (!/^[1-9][0-9]{5}$/.test(a.postalCode ?? '')) e['shippingAddress.postalCode'] = 'postalCode must be a 6-digit PIN code';
  invalid(e);
  const cart = cartView(user.userId);
  if (!cart.items.length) throw new Http(400, 'Your cart is empty');
  const short = cart.items.filter((i) => i.status !== 'AVAILABLE');
  if (short.length) throw new Http(409, `Some items in your cart can't be bought right now: ${short.map((i) => i.message).join('; ')}`);
  const number0 = `ORD-${randomUUID().replace(/-/g, '').slice(0, 12).toUpperCase()}`;
  cart.items.forEach((i) => changeStock(i.productId, 'RESERVE', i.quantity, { reference: number0 }));
  const fee = cart.subtotal >= FREE_SHIPPING_FROM ? 0 : SHIPPING_FEE;
  const number = number0;
  const o = {
    orderId: randomUUID(), orderNumber: number, userId: user.userId, idempotencyKey: key, status: 'PENDING_PAYMENT', statusReason: null,
    items: cart.items.map((i) => ({ productId: i.productId, sku: i.sku, productName: i.name, imageUrl: i.imageUrl, unitPrice: i.unitPrice, listPrice: i.listPrice, quantity: i.quantity, lineTotal: i.lineTotal })),
    itemCount: cart.totalQuantity, subtotal: cart.subtotal, shippingFee: fee, totalAmount: round2(cart.subtotal + fee), currency: 'INR',
    shippingAddress: { ...a, country: 'IN' }, paymentDueAt: new Date(Date.now() + PAYMENT_WINDOW_MS).toISOString(), carrier: null, trackingNumber: null,
    estimatedDeliveryDate: null, placedAt: now(), confirmedAt: null, shippedAt: null, deliveredAt: null, cancelledAt: null, createdAt: now(),
    timeline: [{ fromStatus: null, toStatus: 'CREATED', note: 'Checkout started', changedAt: now() }],
  };
  moveOrder(o, 'PENDING_PAYMENT', 'Stock reserved, waiting for payment');
  db.orders.set(number, o);
  db.carts.set(user.userId, []);
  email(o, 'ORDER_PLACED', `Order ${number} placed - please pay within 30 minutes`);
  later(() => db.payments.set(number, { paymentId: randomUUID(), orderNumber: number, userId: user.userId, status: 'PENDING', amount: o.totalAmount, currency: 'INR', method: null, cardBrand: null, cardLast4: null, upiIdMasked: null, paidAt: null, cancelledAt: null, refundedAt: null, refundReason: null, createdAt: now(), attempts: [] }));
  return ok(orderView(o), 201, 'Order placed successfully');
}, 'user');
const ownOrder = (user, number) => {
  const o = db.orders.get(number);
  if (!o || (o.userId !== user.userId && user.role !== 'ADMIN')) throw new Http(404, `Order not found: ${number}`);
  return o;
};
route('GET', '/api/v1/orders', ({ user, query }) => {
  const status = query.get('status');
  const list = [...db.orders.values()].filter((o) => o.userId === user.userId && (!status || o.status === status)).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  return ok(page(list.map((o) => ({ orderNumber: o.orderNumber, userId: o.userId, status: o.status, itemCount: o.itemCount, totalAmount: o.totalAmount, currency: 'INR', createdAt: o.createdAt, placedAt: o.placedAt })), Number(query.get('page') ?? 0), 10));
}, 'user');
route('GET', '/api/v1/orders/:number', ({ user, params }) => ok(orderView(ownOrder(user, params.number))), 'user');
route('POST', '/api/v1/orders/:number/cancel', ({ user, params, body }) => cancelOrder(ownOrder(user, params.number), body?.reason, 'Cancelled by customer'), 'user');
function cancelOrder(o, reason, fallback) {
  if (o.status === 'CANCELLED') return ok(orderView(o));
  if (!['PENDING_PAYMENT', 'CONFIRMED'].includes(o.status)) throw new Http(409, `Order ${o.orderNumber} can't be cancelled${o.status === 'SHIPPED' ? ' (it is already on its way)' : ''}`);
  const paid = o.status === 'CONFIRMED';
  o.items.forEach((i) => changeStock(i.productId, paid ? 'RETURN' : 'RELEASE', i.quantity, { reference: o.orderNumber }));
  let note = reason?.trim() || fallback;
  if (paid) note += ' - a full refund has been requested';
  moveOrder(o, 'CANCELLED', note);
  log(paid ? 'PAID_ORDER_CANCELLED' : 'ORDER_CANCELLED', paid ? 'WARNING' : 'INFO', paid ? `Paid order ${o.orderNumber} cancelled: ${note} (${inr(o.totalAmount)} to refund)` : `Order ${o.orderNumber} cancelled before payment: ${note}`, o.orderNumber);
  email(o, 'ORDER_CANCELLED', `Order ${o.orderNumber} cancelled`);
  const shipment = db.shipments.get(o.orderNumber);
  if (shipment) track(shipment, 'CANCELLED', 'Cancelled');
  later(() => {
    const p = db.payments.get(o.orderNumber);
    if (!p) return;
    if (p.status === 'SUCCEEDED') {
      Object.assign(p, { status: 'REFUNDED', refundedAt: now(), refundReason: note });
      Object.assign(o, { refundedAmount: o.totalAmount, refundedAt: now() });
      log('REFUND_COMPLETED', 'INFO', `Refund of ${inr(o.totalAmount)} for ${o.orderNumber} completed`, o.orderNumber);
      email(o, 'PAYMENT_REFUNDED', `Refund of ${inr(o.totalAmount)} for ${o.orderNumber}`);
    }
    else if (p.status === 'PENDING') Object.assign(p, { status: 'CANCELLED', cancelledAt: now() });
  });
  return ok(orderView(o));
}

// payments
route('GET', '/api/v1/payments/orders/:number', ({ user, params }) => {
  ownOrder(user, params.number);
  const p = db.payments.get(params.number);
  if (!p) throw new Http(404, `No payment for order ${params.number} yet`);
  return ok(p);
}, 'user');
route('POST', '/api/v1/payments/orders/:number/pay', ({ user, params, body, headers }) => {
  const o = ownOrder(user, params.number);
  if (!headers['idempotency-key']) throw new Http(400, 'The Idempotency-Key header is required');
  const p = db.payments.get(params.number);
  if (!p) throw new Http(404, `No payment for order ${params.number} yet`);
  if (p.status === 'SUCCEEDED') return ok(p);
  if (p.status !== 'PENDING') throw new Http(409, `Order ${o.orderNumber} can no longer be paid`);
  let failure = null;
  const attempt = { attemptId: randomUUID(), method: body.method, cardBrand: null, cardLast4: null, upiIdMasked: null, status: 'SUCCEEDED', failureCode: null, failureReason: null, createdAt: now() };
  if (body.method === 'CARD') {
    const n = (body.card?.number ?? '').replace(/\D/g, '');
    attempt.cardBrand = n.startsWith('4') ? 'VISA' : 'MASTERCARD';
    attempt.cardLast4 = n.slice(-4);
    if (n === '4000000000000002') failure = ['CARD_DECLINED', 'Your card was declined.'];
    if (n === '4000000000009995') failure = ['INSUFFICIENT_FUNDS', 'Your card has insufficient funds.'];
  } else if (body.method === 'UPI') {
    attempt.upiIdMasked = (body.upiId ?? '').replace(/^(.{2}).*(@.*)$/, '$1***$2');
    if ((body.upiId ?? '').toLowerCase().startsWith('fail')) failure = ['UPI_DECLINED', 'The UPI payment was declined.'];
  } else invalid({ method: 'method is required (CARD or UPI)' });
  if (failure) {
    Object.assign(attempt, { status: 'FAILED', failureCode: failure[0], failureReason: failure[1] });
    p.attempts.push(attempt);
    log('PAYMENT_FAILED', 'WARNING', `Payment of ${inr(p.amount)} for ${o.orderNumber} failed: ${failure[1]}`, o.orderNumber);
    throw new Http(402, failure[1]);
  }
  p.attempts.push(attempt);
  Object.assign(p, { status: 'SUCCEEDED', method: attempt.method, cardBrand: attempt.cardBrand, cardLast4: attempt.cardLast4, upiIdMasked: attempt.upiIdMasked, paidAt: now() });
  later(() => {
    if (o.status !== 'PENDING_PAYMENT') return;
    o.items.forEach((i) => changeStock(i.productId, 'SALE', i.quantity, { reference: o.orderNumber }));
    const days = estimateDays(o.shippingAddress.postalCode);
    o.estimatedDeliveryDate = addDays(days);
    log('ORDER_PAID', 'INFO', `Order ${o.orderNumber} paid: ${inr(o.totalAmount)}`, o.orderNumber);
    email(o, 'ORDER_CONFIRMED', `Payment received for ${o.orderNumber}`);
    moveOrder(o, 'CONFIRMED', `Paid by ${attempt.method === 'CARD' ? `${attempt.cardBrand} ending ${attempt.cardLast4}` : 'UPI'}`);
    const s = { id: randomUUID(), orderNumber: o.orderNumber, userId: o.userId, status: 'PENDING', carrier: undefined, trackingNumber: undefined, estimatedDeliveryDate: o.estimatedDeliveryDate, failedAttempts: 0, tracking: [], city: o.shippingAddress.city, state: o.shippingAddress.state, address: o.shippingAddress, items: o.items, createdAt: now() };
    track(s, 'PENDING', 'Order confirmed - preparing your package', 'Bengaluru warehouse');
    db.shipments.set(o.orderNumber, s);
  });
  return ok(p, 200, 'Payment successful');
}, 'user');

// shipping
const shipmentView = (s) => ({ id: s.id, orderNumber: s.orderNumber, status: s.status, carrier: s.carrier, trackingNumber: s.trackingNumber, estimatedDeliveryDate: s.estimatedDeliveryDate, failedAttempts: s.failedAttempts, tracking: s.tracking, items: [], createdAt: s.createdAt });
route('GET', '/api/v1/shipments/orders/:number', ({ user, params }) => {
  ownOrder(user, params.number);
  const s = db.shipments.get(params.number);
  if (!s) throw new Http(404, `No shipment for order ${params.number}`);
  return ok(shipmentView(s));
}, 'user');
route('GET', '/api/v1/shipments/track/:tn', ({ params }) => {
  const s = [...db.shipments.values()].find((x) => x.trackingNumber === params.tn);
  if (!s) throw new Http(404, `No shipment with tracking number ${params.tn}`);
  return ok({ trackingNumber: s.trackingNumber, carrier: s.carrier, status: s.status, estimatedDeliveryDate: s.status === 'DELIVERED' ? undefined : s.estimatedDeliveryDate, destination: `${s.city}, ${s.state}`, events: s.tracking });
});
route('GET', '/api/v1/shipments/delivery-estimate', ({ query }) => {
  const pin = query.get('postalCode') ?? '';
  if (!/^[1-9][0-9]{5}$/.test(pin)) throw new Http(400, 'postalCode must be a 6-digit PIN code');
  const days = estimateDays(pin);
  const d = new Date(addDays(days) + 'T00:00:00');
  return ok({ postalCode: pin, deliveryDays: days, estimatedDeliveryDate: addDays(days), message: `Delivery by ${d.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' }).replace(',', ',')}` });
});
const adminShipmentView = (s) => ({ ...shipmentView(s), statusReason: s.status === 'CANCELLED' ? 'The order was cancelled' : undefined, userId: s.userId,
  deliveryAddress: { recipientName: s.address.fullName, phone: s.address.phone, line1: s.address.line1, line2: s.address.line2, city: s.address.city, state: s.address.state, postalCode: s.address.postalCode, country: 'IN' },
  items: s.items.map((i) => ({ productId: i.productId, sku: i.sku, productName: i.productName, quantity: i.quantity })) });
route('GET', '/api/v1/shipments/admin', ({ query }) => {
  const list = [...db.shipments.values()]
    .filter((s) => (!query.get('orderNumber') || s.orderNumber === query.get('orderNumber')) && (!query.get('status') || s.status === query.get('status')))
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  const summary = (s) => ({ id: s.id, orderNumber: s.orderNumber, status: s.status, carrier: s.carrier, trackingNumber: s.trackingNumber, estimatedDeliveryDate: s.estimatedDeliveryDate, city: s.city, postalCode: s.address.postalCode, itemCount: s.items.reduce((n, i) => n + i.quantity, 0), createdAt: s.createdAt, updatedAt: now() });
  return ok(page(list.map(summary), Number(query.get('page') ?? 0), 20));
}, 'admin');
route('GET', '/api/v1/shipments/admin/:id', ({ params }) => {
  const s = [...db.shipments.values()].find((x) => x.id === params.id);
  if (!s) throw new Http(404, 'Shipment not found');
  return ok(adminShipmentView(s));
}, 'admin');
route('POST', '/api/v1/shipments/admin/:id/:action', ({ params, body }) => {
  const s = [...db.shipments.values()].find((x) => x.id === params.id);
  if (!s) throw new Http(404, 'Shipment not found');
  const o = db.orders.get(s.orderNumber);
  const onTheWay = ['SHIPPED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERY_FAILED'];
  if (params.action === 'pack' && s.status !== 'PENDING') throw new Http(409, `Shipment is ${s.status} and can't be packed`);
  if (params.action === 'ship' && s.status !== 'PACKED') throw new Http(409, `Shipment is ${s.status} and can't be shipped`);
  if (params.action === 'ship' && !['Delhivery', 'Blue Dart', 'Ecom Express', 'India Post'].includes(body.carrier)) throw new Http(400, 'Unknown carrier. Use one of: Delhivery, Blue Dart, Ecom Express, India Post');
  if (params.action === 'tracking') {
    if (!onTheWay.includes(s.status)) throw new Http(409, `Shipment is ${s.status}: scans start after it ships`);
    if (body.status === 'IN_TRANSIT' && !body.location) invalid({ location: 'location is required for IN_TRANSIT' });
    if (body.status === 'DELIVERY_FAILED' && !body.note) invalid({ note: 'note is required for DELIVERY_FAILED' });
  }
  if (params.action === 'pack') track(s, 'PACKED', 'Packed and ready to ship', 'Bengaluru warehouse');
  else if (params.action === 'ship') {
    s.carrier = body.carrier;
    s.trackingNumber = body.trackingNumber || `EC${String(db.seq++).padStart(12, '0')}`;
    track(s, 'SHIPPED', `Shipped with ${body.carrier}`, body.location ?? 'Bengaluru');
    later(() => {
      Object.assign(o, { carrier: s.carrier, trackingNumber: s.trackingNumber });
      moveOrder(o, 'SHIPPED', `Shipped with ${s.carrier} (${s.trackingNumber})`);
      log('ORDER_SHIPPED', 'INFO', `Order ${o.orderNumber} shipped`, o.orderNumber);
      email(o, 'ORDER_SHIPPED', `Your order ${o.orderNumber} has shipped`);
    });
  } else if (params.action === 'tracking') {
    const text = { IN_TRANSIT: 'In transit', OUT_FOR_DELIVERY: 'Out for delivery', DELIVERY_FAILED: `Delivery attempt failed: ${body.note ?? ''}`, DELIVERED: 'Delivered' }[body.status];
    track(s, body.status, text, body.location);
    if (body.status === 'DELIVERY_FAILED') {
      s.failedAttempts += 1;
      log('DELIVERY_FAILED', 'WARNING', `Delivery of ${o.orderNumber} failed (${s.carrier}): ${body.note}`, o.orderNumber);
      email(o, 'SHIPMENT_DELIVERY_FAILED', `We missed you - ${o.orderNumber}`);
    }
    if (body.status === 'DELIVERED') later(() => {
      moveOrder(o, 'DELIVERED', 'Delivered');
      log('ORDER_DELIVERED', 'INFO', `Order ${o.orderNumber} delivered`, o.orderNumber);
      email(o, 'ORDER_DELIVERED', `Delivered: ${o.orderNumber}`);
      db.reviews.filter((r) => r.userId === o.userId && o.items.some((i) => i.productId === r.productId)).forEach((r) => (r.verifiedPurchase = true));
    });
  }
  return ok(adminShipmentView(s));
}, 'admin');

// wishlist
const wishlistOf = (userId) => (db.wishlists.has(userId) ? db.wishlists.get(userId) : (db.wishlists.set(userId, []), db.wishlists.get(userId)));
const wishlistView = (userId) => {
  const items = wishlistOf(userId).map((w) => {
    const p = db.products.get(w.productId);
    const price = sellingPrice(p);
    const avail = available(p.id);
    return { productId: p.id, sku: p.sku, name: p.name, imageUrl: p.imageUrls[0] ?? null, price, listPrice: p.discountPercent > 0 ? p.price : null, priceWhenAdded: w.priceWhenAdded, priceDifference: round2(price - w.priceWhenAdded), priceDropped: price < w.priceWhenAdded, availableQuantity: avail, status: p.status !== 'ACTIVE' ? 'UNAVAILABLE' : avail > 0 ? 'AVAILABLE' : 'OUT_OF_STOCK', message: avail > 0 ? null : `${p.name} is out of stock`, addedAt: w.addedAt };
  });
  return { items, totalItems: items.length, maxItems: 100, currency: 'INR', pricesUpToDate: true, warnings: [] };
};
route('GET', '/api/v1/wishlist', ({ user }) => ok(wishlistView(user.userId)), 'user');
route('POST', '/api/v1/wishlist/items', ({ user, body }) => {
  const p = db.products.get(body.productId);
  if (!p) throw new Http(404, 'Product not found');
  const list = wishlistOf(user.userId);
  const created = !list.some((w) => w.productId === p.id);
  if (created) list.unshift({ productId: p.id, priceWhenAdded: sellingPrice(p), addedAt: now() });
  return ok(wishlistView(user.userId).items.find((i) => i.productId === p.id), created ? 201 : 200);
}, 'user');
route('DELETE', '/api/v1/wishlist/items/:pid', ({ user, params }) => {
  db.wishlists.set(user.userId, wishlistOf(user.userId).filter((w) => w.productId !== params.pid));
  return nothing();
}, 'user');
route('GET', '/api/v1/wishlist/contains', ({ user, query }) => {
  const ids = (query.get('productIds') ?? '').split(',');
  return ok({ productIds: wishlistOf(user.userId).filter((w) => ids.includes(w.productId)).map((w) => w.productId) });
}, 'user');
route('POST', '/api/v1/wishlist/items/:pid/move-to-cart', (ctx) => {
  routes.find((r) => r.method === 'POST' && r.regex.test('/api/v1/cart/items')).handler({ ...ctx, body: { productId: ctx.params.pid, quantity: ctx.body?.quantity ?? 1 } });
  db.wishlists.set(ctx.user.userId, wishlistOf(ctx.user.userId).filter((w) => w.productId !== ctx.params.pid));
  return ok(wishlistView(ctx.user.userId));
}, 'user');

// ---------------------------------------------------------------- the staff side (admin panel)
// Registered ahead of the customer routes, so /orders/admin isn't read as an order number.

const staff = (method, pattern, handler) => {
  route(method, pattern, handler, 'admin');
  routes.unshift(routes.pop());
};
const pageNo = (query) => Number(query.get('page') ?? 0);
const sizeOf = (query, d = 20) => Math.min(100, Number(query.get('size') ?? d));
const newestFirst = (a, b) => b.createdAt.localeCompare(a.createdAt);

// dashboard (admin-service): days are India days, like the real one
const today = () => istDay(now());
const shiftDay = (day, n) => new Date(dayStart(day) + n * 86_400_000 + IST).toISOString().slice(0, 10);
function rangeOf(query) {
  const to = query.get('to') || today();
  const from = query.get('from') || shiftDay(to, -29);
  if (from > to) throw new Http(400, "'from' must be on or before 'to'");
  return { from, to };
}
const inRange = (iso, r) => iso && istDay(iso) >= r.from && istDay(iso) <= r.to;
const units = (o) => o.items.reduce((n, i) => n + i.quantity, 0);
function salesIn(r) {
  const paid = [...db.orders.values()].filter((o) => inRange(o.confirmedAt, r));
  const refunded = [...db.orders.values()].filter((o) => inRange(o.refundedAt, r));
  const gross = round2(paid.reduce((n, o) => n + o.totalAmount, 0));
  const refunds = round2(refunded.reduce((n, o) => n + o.refundedAmount, 0));
  return { paid, refunded, gross, refunds, net: round2(gross - refunds) };
}
staff('GET', '/api/v1/dashboard/summary', ({ query }) => {
  const r = rangeOf(query);
  const days = (dayStart(r.to) - dayStart(r.from)) / 86_400_000 + 1;
  const cur = salesIn(r);
  const prev = salesIn({ from: shiftDay(r.from, -days), to: shiftDay(r.from, -1) });
  const all = [...db.orders.values()];
  const placed = all.filter((o) => inRange(o.placedAt, r));
  const attempts = [...db.payments.values()].flatMap((p) => p.attempts).filter((a) => inRange(a.createdAt, r));
  const succeeded = attempts.filter((a) => a.status === 'SUCCEEDED').length;
  const failed = attempts.length - succeeded;
  const buyers = new Set(cur.paid.map((o) => o.userId));
  const firstPaid = (u) => all.filter((o) => o.userId === u && o.confirmedAt).map((o) => o.confirmedAt).sort()[0];
  const toShip = all.filter((o) => o.status === 'CONFIRMED');
  const weekAgo = shiftDay(today(), -6);
  return ok({
    from: r.from, to: r.to, currency: 'INR',
    sales: {
      grossSales: cur.gross, refunds: cur.refunds, netSales: cur.net, paidOrders: cur.paid.length,
      averageOrderValue: cur.paid.length ? round2(cur.gross / cur.paid.length) : undefined,
      unitsSold: cur.paid.reduce((n, o) => n + units(o), 0), previousPeriodNetSales: prev.net,
      netSalesChangePercent: prev.net ? round2(((cur.net - prev.net) / Math.abs(prev.net)) * 100) : undefined,
    },
    orders: {
      placed: placed.length, paid: cur.paid.length,
      cancelledBeforePayment: all.filter((o) => inRange(o.cancelledAt, r) && !o.confirmedAt).length,
      cancelledAfterPayment: all.filter((o) => inRange(o.cancelledAt, r) && o.confirmedAt).length,
      shipped: all.filter((o) => inRange(o.shippedAt, r)).length, delivered: all.filter((o) => inRange(o.deliveredAt, r)).length,
    },
    payments: { succeeded, failed, successRatePercent: attempts.length ? round2((succeeded / attempts.length) * 100) : undefined, refunds: cur.refunded.length },
    customers: { buyers: buyers.size, newBuyers: [...buyers].filter((u) => inRange(firstPaid(u), r)).length },
    workload: {
      ordersToShip: toShip.length, oldestToShipPaidAt: toShip.map((o) => o.confirmedAt).sort()[0],
      ordersInTransit: all.filter((o) => o.status === 'SHIPPED').length,
      lowStockProducts: [...db.stock.keys()].filter((id) => db.products.get(id).status !== 'DISCONTINUED' && available(id) <= db.stock.get(id).threshold).length,
      deliveryFailuresLast7Days: db.activity.filter((a) => a.kind === 'DELIVERY_FAILED' && istDay(a.occurredAt) >= weekAgo).length,
    },
  });
});
function daily(r) {
  const out = [];
  for (let d = r.from; d <= r.to; d = shiftDay(d, 1)) {
    const s = salesIn({ from: d, to: d });
    out.push({ date: d, paidOrders: s.paid.length, grossSales: s.gross, refunds: s.refunds, netSales: s.net, unitsSold: s.paid.reduce((n, o) => n + units(o), 0) });
  }
  return out;
}
staff('GET', '/api/v1/dashboard/sales/daily', ({ query }) => ok(daily(rangeOf(query))));
staff('GET', '/api/v1/dashboard/sales/daily.csv', ({ query }) => {
  const r = rangeOf(query);
  const rows = daily(r).map((d) => [d.date, d.paidOrders, d.grossSales.toFixed(2), d.refunds.toFixed(2), d.netSales.toFixed(2), d.unitsSold, 'INR'].join(','));
  return { status: 200, contentType: 'text/csv', disposition: `attachment; filename="sales-${r.from}-to-${r.to}.csv"`, raw: ['date,paid_orders,gross_sales,refunds,net_sales,units_sold,currency', ...rows].join('\n') + '\n' };
});
staff('GET', '/api/v1/dashboard/products/top', ({ query }) => {
  const r = rangeOf(query);
  const by = query.get('by') ?? 'revenue';
  if (!['revenue', 'units'].includes(by)) throw new Http(400, "'by' must be revenue or units");
  const rows = new Map();
  for (const o of salesIn(r).paid)
    for (const i of o.items) {
      const t = rows.get(i.productId) ?? { productId: i.productId, sku: i.sku, name: i.productName, unitsSold: 0, revenue: 0, orders: 0 };
      t.unitsSold += i.quantity;
      t.revenue = round2(t.revenue + i.lineTotal);
      t.orders += 1;
      rows.set(i.productId, t);
    }
  const key = by === 'units' ? 'unitsSold' : 'revenue';
  return ok([...rows.values()].sort((a, b) => b[key] - a[key]).slice(0, Number(query.get('limit') ?? 10)));
});
staff('GET', '/api/v1/dashboard/orders/pipeline', () => {
  const open = ['PENDING_PAYMENT', 'CONFIRMED', 'SHIPPED'];
  return ok(open.map((status) => {
    const list = [...db.orders.values()].filter((o) => o.status === status);
    return { status, orders: list.length, oldestSince: list.map((o) => o.timeline.at(-1).changedAt).sort()[0] };
  }));
});
staff('GET', '/api/v1/dashboard/inventory/low-stock', ({ query }) => {
  const list = [...db.stock.entries()]
    .filter(([id, s]) => db.products.get(id).status !== 'DISCONTINUED' && available(id) <= s.threshold)
    .map(([id, s]) => {
      const p = db.products.get(id);
      return { productId: id, sku: p.sku, name: p.name, available: available(id), onHand: s.onHand, reserved: s.reserved, threshold: s.threshold, outOfStock: available(id) === 0, updatedAt: s.updatedAt ?? p.updatedAt };
    })
    .sort((a, b) => a.available - b.available || a.name.localeCompare(b.name));
  return ok(list.slice(0, Number(query.get('limit') ?? 50)));
});
staff('GET', '/api/v1/dashboard/activity', ({ query }) => {
  const severity = query.get('severity');
  return ok(page(db.activity.filter((a) => !severity || a.severity === severity), pageNo(query), sizeOf(query)));
});

// orders, payments, emails
const orderSummary = (o) => ({ orderNumber: o.orderNumber, userId: o.userId, status: o.status, itemCount: o.itemCount, totalAmount: o.totalAmount, currency: 'INR', createdAt: o.createdAt, placedAt: o.placedAt });
staff('GET', '/api/v1/orders/admin', ({ query }) => {
  const list = [...db.orders.values()]
    .filter((o) => (!query.get('status') || o.status === query.get('status')) && (!query.get('userId') || o.userId === query.get('userId')))
    .sort(newestFirst);
  return ok(page(list.map(orderSummary), pageNo(query), sizeOf(query)));
});
staff('POST', '/api/v1/orders/admin/:number/cancel', ({ params, body }) => {
  const o = db.orders.get(params.number);
  if (!o) throw new Http(404, `Order not found: ${params.number}`);
  return cancelOrder(o, body?.reason, 'Cancelled by admin');
});
staff('GET', '/api/v1/payments/admin', ({ query }) => {
  const list = [...db.payments.values()].filter((p) => !query.get('status') || p.status === query.get('status')).sort(newestFirst);
  return ok(page(list.map((p) => ({ orderNumber: p.orderNumber, status: p.status, amount: p.amount, currency: 'INR', method: p.method, paidAt: p.paidAt, createdAt: p.createdAt })), pageNo(query), sizeOf(query)));
});
staff('GET', '/api/v1/notifications/admin', ({ query }) => {
  const list = db.emails.filter((e) => !query.get('orderNumber') || e.orderNumber === query.get('orderNumber'));
  return ok(page(list, pageNo(query), sizeOf(query)));
});

// catalog (product-service): staff see every status
const SORTS = { createdAt: (p) => p.createdAt, name: (p) => p.name.toLowerCase(), price: (p) => p.price };
staff('GET', '/api/v1/products', ({ query }) => {
  const q = (query.get('search') ?? '').toLowerCase();
  const cats = query.get('categoryId') ? descendants(query.get('categoryId')) : null;
  const [field, dir] = (query.get('sort') ?? 'createdAt,desc').split(',');
  const key = SORTS[field] ?? SORTS.createdAt;
  const list = [...db.products.values()]
    .filter((p) => (!query.get('status') || p.status === query.get('status')) && (!cats || cats.includes(p.categoryId)))
    .filter((p) => !q || [p.name, p.sku, p.brand ?? ''].some((v) => v.toLowerCase().includes(q)))
    .sort((a, b) => (key(a) > key(b) ? 1 : key(a) < key(b) ? -1 : 0) * (dir === 'asc' ? 1 : -1));
  return ok(page(list.map(productView), pageNo(query), sizeOf(query)));
});
staff('PUT', '/api/v1/products/:id', ({ params, body }) => {
  const p = db.products.get(params.id);
  if (!p) throw new Http(404, `Product not found: ${params.id}`);
  checkProduct(body);
  if ([...db.products.values()].some((x) => x.sku === body.sku && x.id !== p.id)) throw new Http(409, `A product with SKU ${body.sku} already exists`);
  Object.assign(p, { sku: body.sku, name: body.name.trim(), description: body.description ?? null, brand: body.brand || null, categoryId: body.categoryId ?? p.categoryId, price: Number(body.price), discountPercent: Number(body.discountPercent ?? 0), imageUrls: body.imageUrls ?? [], status: body.status ?? p.status, updatedAt: now() });
  return ok(productView(p));
});
staff('DELETE', '/api/v1/products/:id', ({ params }) => {
  const p = db.products.get(params.id);
  if (!p) throw new Http(404, `Product not found: ${params.id}`);
  Object.assign(p, { status: 'DISCONTINUED', updatedAt: now() });
  return nothing();
});
function checkCategory(body, id) {
  if (!body.name?.trim()) invalid({ name: 'name is required' });
  if (body.parentId && !category(body.parentId)) throw new Http(404, 'Parent category not found');
  if (id && body.parentId && descendants(id).includes(body.parentId)) throw new Http(400, 'A category cannot be moved under itself or one of its sub-categories');
  const name = body.name.trim();
  if (db.categories.some((c) => c.id !== id && (c.parentId ?? null) === (body.parentId ?? null) && c.name.toLowerCase() === name.toLowerCase()))
    throw new Http(409, `A category named '${name}' already exists at this level`);
}
staff('POST', '/api/v1/categories', ({ body }) => {
  checkCategory(body);
  const c = addCategory(body.name.trim(), body.parentId ?? null, body.description || null);
  return ok({ id: c.id, name: c.name, description: c.description, createdAt: now(), updatedAt: now() }, 201);
});
staff('PUT', '/api/v1/categories/:id', ({ params, body }) => {
  const c = category(params.id);
  if (!c) throw new Http(404, 'Category not found');
  checkCategory(body, c.id);
  Object.assign(c, { name: body.name.trim(), description: body.description || null, parentId: body.parentId ?? null });
  return ok({ id: c.id, name: c.name, description: c.description });
});
staff('DELETE', '/api/v1/categories/:id', ({ params }) => {
  const c = category(params.id);
  if (!c) throw new Http(404, 'Category not found');
  if (db.categories.some((x) => x.parentId === c.id)) throw new Http(409, 'Category has sub-categories. Delete or move them first.');
  if ([...db.products.values()].some((p) => p.categoryId === c.id)) throw new Http(409, 'Category has products. Move or delete them first.');
  db.categories.splice(db.categories.indexOf(c), 1);
  return nothing();
});

// inventory
staff('GET', '/api/v1/inventory', ({ query }) => {
  const q = (query.get('search') ?? '').toLowerCase();
  const list = [...db.stock.keys()]
    .map(stockView)
    .filter((i) => (query.get('lowStock') !== 'true' || i.lowStock) && (!q || i.productName.toLowerCase().includes(q) || i.sku.toLowerCase().includes(q)))
    .sort((a, b) => a.availableQuantity - b.availableQuantity || a.productName.localeCompare(b.productName));
  return ok(page(list, pageNo(query), sizeOf(query)));
});
staff('POST', '/api/v1/inventory/:id/stock/adjustments', ({ params, body }) => {
  const s = db.stock.get(params.id);
  if (!s) throw new Http(404, 'Inventory item not found');
  const change = Number(body.quantityChange);
  if (!Number.isInteger(change)) invalid({ quantityChange: 'quantityChange is required' });
  if (change === 0) throw new Http(400, 'quantityChange cannot be 0');
  if (s.onHand + change < s.reserved) throw new Http(409, `Can't remove ${-change}: only ${available(params.id)} available (${s.reserved} reserved for orders)`);
  changeStock(params.id, change > 0 ? 'RESTOCK' : 'ADJUSTMENT', change, { reason: body.reason ?? null });
  return ok(stockView(params.id));
});
staff('PUT', '/api/v1/inventory/:id/threshold', ({ params, body }) => {
  const s = db.stock.get(params.id);
  if (!s) throw new Http(404, 'Inventory item not found');
  const t = Number(body.lowStockThreshold);
  if (!(Number.isInteger(t) && t >= 0)) invalid({ lowStockThreshold: 'lowStockThreshold cannot be negative' });
  Object.assign(s, { threshold: t, updatedAt: now() });
  checkLow(params.id);
  return ok(stockView(params.id));
});
staff('GET', '/api/v1/inventory/:id/movements', ({ params, query }) => {
  if (!db.stock.get(params.id)) throw new Http(404, 'Inventory item not found');
  return ok(page(db.movements.filter((m) => m.productId === params.id).map(({ productId, ...m }) => m), pageNo(query), sizeOf(query)));
});

// reviews
const adminReview = (r) => ({ ...reviewView(r, true), userId: r.userId });
staff('GET', '/api/v1/reviews/admin', ({ query }) => {
  const list = db.reviews.filter((r) => (!query.get('status') || r.status === query.get('status')) && (!query.get('productId') || r.productId === query.get('productId'))).sort(newestFirst);
  return ok(page(list.map(adminReview), pageNo(query), sizeOf(query)));
});
staff('POST', '/api/v1/reviews/admin/:id/:action', ({ params, body }) => {
  const r = db.reviews.find((x) => x.id === params.id);
  if (!r) throw new Http(404, 'Review not found');
  if (params.action === 'hide') {
    if (!body.reason?.trim()) invalid({ reason: 'reason is required' });
    Object.assign(r, { status: 'HIDDEN', hiddenReason: body.reason.trim() });
  } else if (params.action === 'unhide') Object.assign(r, { status: 'VISIBLE', hiddenReason: null });
  else throw new Http(404, 'No such action');
  return ok(adminReview(r));
});

// customers (user-service)
staff('GET', '/api/v1/admin/users', ({ query }) => {
  const list = [...db.users.values()].filter((u) => (!query.get('role') || u.role === query.get('role')) && (!query.get('status') || u.accountStatus === query.get('status'))).sort(newestFirst);
  return ok(page(list.map(profile), pageNo(query), sizeOf(query)));
});
staff('PUT', '/api/v1/admin/users/:id/status', ({ params, body, user }) => {
  const u = db.users.get(params.id);
  if (!u) throw new Http(404, 'User not found');
  if (!['ACTIVE', 'INACTIVE', 'SUSPENDED', 'LOCKED'].includes(body.status)) invalid({ status: 'status must be ACTIVE, INACTIVE, SUSPENDED or LOCKED' });
  if (u.userId === user.userId) throw new Http(400, 'Admins cannot change their own account status');
  Object.assign(u, { accountStatus: body.status, updatedAt: now() });
  return ok(profile(u));
});

route('POST', '/__mock/reset', () => (seed(), nothing()));

// ---------------------------------------------------------------- server

seed();
http
  .createServer(async (req, res) => {
    const url = new URL(req.url, 'http://localhost');
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    let body = {};
    try {
      body = chunks.length ? JSON.parse(Buffer.concat(chunks).toString()) : {};
    } catch {
      return send(res, 400, { status: 400, error: 'BAD_REQUEST', message: 'Malformed JSON request body', path: url.pathname });
    }
    const r = routes.find((x) => x.method === req.method && x.regex.test(url.pathname));
    if (!r) return send(res, 404, { status: 404, error: 'NOT_FOUND', message: `No route for ${req.method} ${url.pathname}`, path: url.pathname });
    const user = caller(req);
    if (r.access !== 'public' && !user) return send(res, 401, { status: 401, error: 'UNAUTHORIZED', message: 'Authentication required: missing or invalid token', path: url.pathname });
    if (r.access === 'admin' && user.role !== 'ADMIN') return send(res, 403, { status: 403, error: 'FORBIDDEN', message: 'Access denied', path: url.pathname });
    const match = r.regex.exec(url.pathname);
    const params = Object.fromEntries(r.keys.map((k, i) => [k, decodeURIComponent(match[i + 1])]));
    try {
      const result = r.handler({ params, query: url.searchParams, body, user, headers: req.headers });
      if (result.raw !== undefined) {
        res.writeHead(result.status, { 'Content-Type': result.contentType, 'Content-Disposition': result.disposition ?? '' });
        return res.end(result.raw);
      }
      send(res, result.status, result.body);
    } catch (e) {
      if (!(e instanceof Http)) console.error(e);
      const status = e instanceof Http ? e.status : 500;
      send(res, status, { timestamp: now(), status, error: http.STATUS_CODES[status], message: e.message, path: url.pathname, validationErrors: e.validationErrors });
    }
  })
  .listen(PORT, () => console.log(`mock api on http://localhost:${PORT}`));

function send(res, status, body) {
  res.writeHead(status, body === undefined ? {} : { 'Content-Type': 'application/json' });
  res.end(body === undefined ? undefined : JSON.stringify(body));
}

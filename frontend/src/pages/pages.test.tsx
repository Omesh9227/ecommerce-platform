import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mockApi, renderAt, signIn } from '../test/render';
import { CartPage } from './CartPage';
import { OrderPage } from './OrderPage';
import { PayPage } from './PayPage';
import { ProductPage } from './ProductPage';
import { SearchPage } from './SearchPage';

afterEach(() => vi.unstubAllGlobals());

const hit = (id: string, name: string, brand: string, price: number) => ({
  productId: id, sku: id, name, brand, price, sellingPrice: price, discountPercent: 0, currency: 'INR', averageRating: 4.5,
  reviewCount: 3, inStock: true, availableQuantity: 5, categoryId: 'c1', categoryName: 'Phones',
});
const facets = {
  brands: [{ value: 'Google', count: 2 }, { value: 'Samsung', count: 1 }],
  categories: [{ id: 'c1', name: 'Phones', count: 3 }],
  priceRanges: [{ label: '₹20,000 & above', min: 20000, count: 3 }],
  ratings: [{ minRating: 4, label: '4 stars & up', count: 3 }],
  availability: { inStock: 3, outOfStock: 0 },
};
const page = <T,>(content: T[]) => ({ content, page: 0, size: 24, totalElements: content.length, totalPages: 1, first: true, last: true });

describe('search page', () => {
  it('shows the corrected query, results and filter counts; filters go into the address', async () => {
    const calls = mockApi((url) =>
      url.pathname === '/api/v1/search/products'
        ? { body: { query: 'pixle', correctedQuery: 'pixel', results: page([hit('p1', 'Pixel 9', 'Google', 71999)]), facets } }
        : undefined,
    );
    renderAt('/search?q=pixle', '/search', <SearchPage />);
    expect(await screen.findByTestId('corrected')).toHaveTextContent('Showing results for pixel instead of “pixle”.');
    expect(screen.getByRole('heading', { name: 'Results for “pixel”' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Pixel 9/ })).toHaveAttribute('href', '/products/p1');

    await userEvent.click(screen.getByRole('checkbox', { name: /Samsung/ }));
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/search?q=pixle&brand=Samsung'));
    await waitFor(() => expect(calls.at(-1)!.url.searchParams.getAll('brand')).toEqual(['Samsung']));

    await userEvent.selectOptions(screen.getByLabelText('Sort by'), 'price_asc');
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('sort=price_asc'));
  });
});

describe('product page', () => {
  const product = {
    id: 'p1', sku: 'PIX-9', name: 'Pixel 9', description: 'A phone.', brand: 'Google', category: { id: 'c1', name: 'Phones' },
    price: 79999, discountPercent: 10, sellingPrice: 71999.1, currency: 'INR', status: 'ACTIVE', availableQuantity: 3, inStock: true,
    averageRating: 4.5, reviewCount: 2, imageUrls: [],
  };
  const answer = (url: URL, init: RequestInit) => {
    if (url.pathname === '/api/v1/products/p1') return { body: product };
    if (url.pathname.startsWith('/api/v1/recommendations/products/')) return { body: { title: 'Similar products', products: [] } };
    if (url.pathname === '/api/v1/recommendations/views') return { status: 204 };
    if (url.pathname === '/api/v1/reviews/products/p1/summary') return { body: { productId: 'p1', averageRating: 4.5, reviewCount: 2, ratingCounts: { 5: 1, 4: 1, 3: 0, 2: 0, 1: 0 } } };
    if (url.pathname === '/api/v1/reviews/products/p1') return { body: page([]) };
    if (url.pathname === '/api/v1/wishlist/contains') return { body: { productIds: [] } };
    if (url.pathname === '/api/v1/cart/items' && init.method === 'POST') return { status: 409, body: { status: 409, message: 'Only 3 units of Pixel 9 are available (you already have 2 in your cart)' } };
    return undefined;
  };

  it('sends guests to sign in before adding to the cart', async () => {
    mockApi(answer);
    renderAt('/products/p1', '/products/:id', <ProductPage />);
    expect(await screen.findByRole('heading', { name: 'Pixel 9' })).toBeInTheDocument();
    expect(screen.getByTestId('stock')).toHaveTextContent('Only 3 left');
    expect(screen.getAllByLabelText('₹71,999.10')[0]).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add to cart' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/login');
  });

  it('records the view and shows the cart’s answer when stock runs short', async () => {
    signIn();
    const calls = mockApi(answer);
    renderAt('/products/p1', '/products/:id', <ProductPage />);
    await screen.findByRole('heading', { name: 'Pixel 9' });
    await waitFor(() => expect(calls.some((c) => c.url.pathname === '/api/v1/recommendations/views' && (c.body as { productId: string }).productId === 'p1')).toBe(true));
    await userEvent.selectOptions(screen.getByLabelText('Quantity'), '2');
    await userEvent.click(screen.getByRole('button', { name: 'Add to cart' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Only 3 units of Pixel 9 are available');
    expect(calls.find((c) => c.method === 'POST' && c.url.pathname === '/api/v1/cart/items')!.body).toEqual({ productId: 'p1', quantity: 2 });
  });
});

describe('cart page', () => {
  const line = (quantity: number) => ({
    productId: 'p1', sku: 'LAMP', name: 'Desk Lamp', imageUrl: null, quantity, unitPrice: 1499, listPrice: null, priceWhenAdded: 1299,
    priceChanged: true, lineTotal: 1499 * quantity, availableQuantity: 4, status: 'AVAILABLE', message: null,
  });
  const cart = (quantity: number) => ({ items: quantity ? [line(quantity)] : [], totalQuantity: quantity, subtotal: 1499 * quantity, totalSavings: 0, currency: 'INR', checkoutReady: quantity > 0, pricesUpToDate: true, warnings: [] });

  it('changes quantities, tells about price changes and empties', async () => {
    signIn();
    let quantity = 1;
    const calls = mockApi((url, init) => {
      if (url.pathname === '/api/v1/cart' ) return { body: cart(quantity) };
      if (url.pathname === '/api/v1/cart/items/p1' && init.method === 'PUT') {
        quantity = JSON.parse(String(init.body)).quantity;
        return { body: cart(quantity) };
      }
      if (url.pathname === '/api/v1/cart/items/p1' && init.method === 'DELETE') return { body: cart((quantity = 0)) };
      return undefined;
    });
    renderAt('/cart', '/cart', <CartPage />);
    expect(await screen.findByText('(was ₹1,299.00 when you added it)')).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('Qty'), '3');
    await waitFor(() => expect(within(screen.getByTestId('cart-subtotal')).getByLabelText('₹4,497.00')).toBeInTheDocument());
    expect(calls.find((c) => c.method === 'PUT')!.body).toEqual({ quantity: 3 });
    await userEvent.click(screen.getByRole('button', { name: 'Remove' }));
    expect(await screen.findByText('Your cart is empty')).toBeInTheDocument();
  });
});

const order = (status: string) => ({
  orderId: 'o1', orderNumber: 'ORD-1', status, statusReason: null, items: [{ productId: 'p1', sku: 'L', productName: 'Desk Lamp', imageUrl: null, unitPrice: 1499, listPrice: null, quantity: 1, lineTotal: 1499 }],
  itemCount: 1, subtotal: 1499, shippingFee: 0, totalAmount: 1499, currency: 'INR',
  shippingAddress: { fullName: 'Omesh Kumar', phone: '9876543210', line1: '12 MG Road', city: 'Bengaluru', state: 'Karnataka', postalCode: '560001' },
  paymentDueAt: new Date(Date.now() + 600_000).toISOString(), carrier: null, trackingNumber: null, estimatedDeliveryDate: null,
  placedAt: new Date().toISOString(), confirmedAt: status === 'CONFIRMED' ? new Date().toISOString() : null, shippedAt: null, deliveredAt: null,
  cancelledAt: null, createdAt: new Date().toISOString(), timeline: [],
});
const payment = (status: string, attempts: unknown[] = []) => ({ paymentId: 'pay1', orderNumber: 'ORD-1', status, amount: 1499, currency: 'INR', method: status === 'SUCCEEDED' ? 'CARD' : null, cardBrand: 'VISA', cardLast4: '4242', upiIdMasked: null, paidAt: null, refundedAt: null, refundReason: null, attempts });

describe('pay page', () => {
  it('shows a decline, then pays with a new Idempotency-Key and opens the order', async () => {
    signIn();
    let attempt = 0;
    const calls = mockApi((url, init) => {
      if (url.pathname === '/api/v1/orders/ORD-1') return { body: order('PENDING_PAYMENT') };
      if (url.pathname === '/api/v1/payments/orders/ORD-1') return { body: payment('PENDING') };
      if (url.pathname === '/api/v1/payments/orders/ORD-1/pay' && init.method === 'POST') {
        attempt++;
        return attempt === 1 ? { status: 402, body: { status: 402, message: 'Your card was declined.' } } : { body: payment('SUCCEEDED') };
      }
      return undefined;
    });
    renderAt('/orders/ORD-1/pay', '/orders/:orderNumber/pay', <PayPage />);
    await userEvent.type(await screen.findByLabelText('Card number'), '4000000000000002');
    await userEvent.type(screen.getByLabelText('Expiry (MM/YY)'), '1230');
    await userEvent.type(screen.getByLabelText('CVV'), '123');
    await userEvent.click(screen.getByRole('button', { name: 'Pay ₹1,499.00' }));
    expect(await screen.findByTestId('payment-error')).toHaveTextContent('Your card was declined.');

    await userEvent.clear(screen.getByLabelText('Card number'));
    await userEvent.type(screen.getByLabelText('Card number'), '4242424242424242');
    await userEvent.click(screen.getByRole('button', { name: 'Pay ₹1,499.00' }));
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/orders/ORD-1'));

    const pays = calls.filter((c) => c.url.pathname.endsWith('/pay'));
    expect(pays).toHaveLength(2);
    expect(pays[1].body).toEqual({ method: 'CARD', card: { number: '4242424242424242', expiryMonth: 12, expiryYear: 2030, cvv: '123' } });
    expect(pays[0].headers['Idempotency-Key']).toBeTruthy();
    expect(pays[0].headers['Idempotency-Key']).not.toBe(pays[1].headers['Idempotency-Key']);
  });

  it('won’t send a mistyped card', async () => {
    signIn();
    const calls = mockApi((url) =>
      url.pathname === '/api/v1/orders/ORD-1' ? { body: order('PENDING_PAYMENT') } : url.pathname === '/api/v1/payments/orders/ORD-1' ? { body: payment('PENDING') } : undefined,
    );
    renderAt('/orders/ORD-1/pay', '/orders/:orderNumber/pay', <PayPage />);
    await userEvent.type(await screen.findByLabelText('Card number'), '4242424242424241');
    await userEvent.click(screen.getByRole('button', { name: 'Pay ₹1,499.00' }));
    expect(screen.getByText('Check the card number')).toBeInTheDocument();
    expect(calls.some((c) => c.url.pathname.endsWith('/pay'))).toBe(false);
  });
});

describe('order page', () => {
  it('cancels a paid order with a reason and says a refund is coming', async () => {
    signIn();
    let status = 'CONFIRMED';
    const calls = mockApi((url, init) => {
      if (url.pathname === '/api/v1/orders/ORD-1') return { body: { ...order(status), statusReason: status === 'CANCELLED' ? 'Ordered by mistake - a full refund has been requested' : null } };
      if (url.pathname === '/api/v1/orders/ORD-1/cancel' && init.method === 'POST') {
        status = 'CANCELLED';
        return { body: { ...order('CANCELLED'), statusReason: 'Ordered by mistake - a full refund has been requested' } };
      }
      if (url.pathname === '/api/v1/payments/orders/ORD-1') return { body: payment('SUCCEEDED') };
      if (url.pathname === '/api/v1/shipments/orders/ORD-1') return { status: 404, body: { status: 404, message: 'No shipment' } };
      return undefined;
    });
    renderAt('/orders/ORD-1', '/orders/:orderNumber', <OrderPage />);
    expect(await screen.findByTestId('order-status')).toHaveTextContent('Paid, preparing');
    await userEvent.click(screen.getByRole('button', { name: 'Cancel order' }));
    expect(screen.getByText('You’ll get a full refund of ₹1,499.00.')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Reason (optional)'), 'Ordered by mistake');
    await userEvent.click(within(screen.getByRole('form', { name: 'Cancel order' })).getByRole('button', { name: 'Cancel order' }));
    expect(await screen.findByText('Ordered by mistake - a full refund has been requested')).toBeInTheDocument();
    expect(screen.getByTestId('order-status')).toHaveTextContent('Cancelled');
    expect(calls.find((c) => c.url.pathname.endsWith('/cancel'))!.body).toEqual({ reason: 'Ordered by mistake' });
  });
});

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { RequireAdmin } from '../auth/RequireAdmin';
import { fakeToken, mockApi, renderAt, signIn } from '../test/render';
import { CustomersPage } from './pages/CustomersPage';
import { DashboardPage } from './pages/DashboardPage';
import { InventoryPage } from './pages/InventoryPage';
import { ProductFormPage, validateProduct } from './pages/ProductFormPage';
import { ShipmentPage } from './pages/ShipmentsPage';

afterEach(() => vi.unstubAllGlobals());

const signInAsAdmin = () => window.localStorage.setItem('cartwheel.token', fakeToken({ sub: 'admin-1', email: 'admin@ecommerce.com', role: 'ADMIN' }));
const page = <T,>(content: T[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1, first: true, last: true });

describe('the admin area', () => {
  it('sends guests to sign in and tells customers it is for staff', () => {
    renderAt('/admin', '/admin', <RequireAdmin>staff only</RequireAdmin>);
    expect(screen.getByTestId('location')).toHaveTextContent('/login');
  });

  it('turns customers away', () => {
    signIn();
    renderAt('/admin', '/admin', <RequireAdmin>staff only</RequireAdmin>);
    expect(screen.getByRole('heading', { name: 'This area is for shop staff' })).toBeInTheDocument();
    expect(screen.queryByText('staff only')).not.toBeInTheDocument();
  });

  it('lets admins in', () => {
    signInAsAdmin();
    renderAt('/admin', '/admin', <RequireAdmin>staff only</RequireAdmin>);
    expect(screen.getByText('staff only')).toBeInTheDocument();
  });
});

describe('the product form', () => {
  const valid = { sku: 'LAMP-01', name: 'Desk Lamp', brand: '', categoryId: 'c1', price: '1499', discountPercent: '0', status: 'ACTIVE' as const, description: '', imageUrls: '' };

  it('uses product-service’s rules', () => {
    expect(validateProduct(valid)).toEqual({});
    expect(validateProduct({ ...valid, sku: 'LA' }).sku).toBeDefined();
    expect(validateProduct({ ...valid, sku: 'LAMP 01' }).sku).toBeDefined();
    expect(validateProduct({ ...valid, price: '0' }).price).toBeDefined();
    expect(validateProduct({ ...valid, price: '10.999' }).price).toBeDefined();
    expect(validateProduct({ ...valid, discountPercent: '100' }).discountPercent).toBeDefined();
    expect(validateProduct({ ...valid, categoryId: '' }).categoryId).toBe('Choose a category');
    expect(validateProduct({ ...valid, imageUrls: 'ftp://x/y.png' }).imageUrls).toMatch(/http/);
    expect(validateProduct({ ...valid, imageUrls: Array.from({ length: 11 }, (_, i) => `https://x/${i}.png`).join('\n') }).imageUrls).toBe('At most 10 photos');
  });

  it('checks before sending, then shows the service’s answer next to the field', async () => {
    signInAsAdmin();
    const calls = mockApi((url, init) => {
      if (url.pathname === '/api/v1/categories') return { body: [{ id: 'c1', name: 'Lighting', children: [] }] };
      if (url.pathname === '/api/v1/products' && init.method === 'POST')
        return { status: 409, body: { status: 409, message: 'A product with SKU LAMP-01 already exists', validationErrors: { sku: 'This SKU is taken' } } };
      return undefined;
    });
    renderAt('/admin/products/new', '/admin/products/:id', <ProductFormPage />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Add product' }));
    expect(screen.getByText('Choose a category')).toBeInTheDocument();
    expect(calls.filter((c) => c.method === 'POST')).toHaveLength(0);

    await user.type(screen.getByLabelText('Name'), 'Desk Lamp');
    await user.type(screen.getByLabelText('SKU'), 'LAMP-01');
    await user.selectOptions(await screen.findByLabelText('Category'), 'Lighting');
    await user.type(screen.getByLabelText('Price (₹)'), '1499');
    await user.clear(screen.getByLabelText('Discount (%)'));
    await user.type(screen.getByLabelText('Discount (%)'), '10');
    expect(screen.getByText('Customers pay ₹1,349.10')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Add product' }));
    expect(await screen.findByText('This SKU is taken')).toBeInTheDocument();
    expect(calls.find((c) => c.method === 'POST')?.body).toMatchObject({ sku: 'LAMP-01', price: 1499, discountPercent: 10, categoryId: 'c1', status: 'ACTIVE', imageUrls: [] });
  });
});

describe('a shipment', () => {
  const shipment = (status: string) => ({
    id: 's1', orderNumber: 'ORD-1', status, failedAttempts: 0, tracking: [], createdAt: '2026-10-30T05:00:00Z',
    items: [{ productId: 'p1', sku: 'LAMP-01', productName: 'Desk Lamp', quantity: 2 }],
    deliveryAddress: { recipientName: 'Asha Rao', phone: '9876543210', line1: '12 MG Road', city: 'Bengaluru', state: 'Karnataka', postalCode: '560001' },
  });

  it('is handed to the chosen carrier once packed', async () => {
    signInAsAdmin();
    const calls = mockApi((url, init) => {
      if (url.pathname === '/api/v1/shipments/admin/s1') return { body: shipment('PACKED') };
      if (url.pathname === '/api/v1/shipments/admin/s1/ship' && init.method === 'POST') return { body: { ...shipment('SHIPPED'), carrier: 'Blue Dart', trackingNumber: 'BD123' } };
      return undefined;
    });
    renderAt('/admin/shipments/s1', '/admin/shipments/:id', <ShipmentPage />);
    const user = userEvent.setup();
    const form = await screen.findByRole('form', { name: 'Ship' });
    await user.selectOptions(within(form).getByLabelText('Carrier'), 'Blue Dart');
    await user.click(within(form).getByRole('button', { name: 'Mark as shipped' }));
    expect(await screen.findByTestId('shipment-status')).toHaveTextContent('Shipped');
    expect(calls.find((c) => c.url.pathname.endsWith('/ship'))?.body).toEqual({ carrier: 'Blue Dart' });
  });

  it('needs a hub for an in-transit scan and a reason for a failed delivery', async () => {
    signInAsAdmin();
    const calls = mockApi((url, init) => {
      if (url.pathname === '/api/v1/shipments/admin/s1') return { body: shipment('SHIPPED') };
      if (url.pathname === '/api/v1/shipments/admin/s1/tracking' && init.method === 'POST') return { body: shipment('DELIVERY_FAILED') };
      return undefined;
    });
    renderAt('/admin/shipments/s1', '/admin/shipments/:id', <ShipmentPage />);
    const user = userEvent.setup();
    const form = await screen.findByRole('form', { name: 'Carrier scan' });
    await user.click(within(form).getByRole('button', { name: 'Save scan' }));
    expect(within(form).getByText('Enter the hub where it was scanned.')).toBeInTheDocument();
    await user.click(within(form).getByLabelText('Delivery attempt failed'));
    await user.click(within(form).getByRole('button', { name: 'Save scan' }));
    expect(within(form).getByText('Enter why the delivery failed.')).toBeInTheDocument();
    expect(calls.filter((c) => c.method === 'POST')).toHaveLength(0);
    await user.type(within(form).getByLabelText('Why it failed'), 'Customer not available');
    await user.click(within(form).getByRole('button', { name: 'Save scan' }));
    await waitFor(() => expect(screen.getByTestId('shipment-status')).toHaveTextContent('Delivery attempt failed'));
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({ status: 'DELIVERY_FAILED', note: 'Customer not available' });
  });
});

describe('stock', () => {
  const item = (onHand: number) => ({
    productId: 'p1', sku: 'LAMP-01', productName: 'Desk Lamp', productStatus: 'ACTIVE', quantityOnHand: onHand, quantityReserved: 2,
    availableQuantity: onHand - 2, lowStockThreshold: 5, lowStock: onHand - 2 <= 5, updatedAt: '2026-10-30T05:00:00Z',
  });

  it('is adjusted by a whole, non-zero number, removals included', async () => {
    signInAsAdmin();
    let onHand = 10;
    const calls = mockApi((url, init) => {
      if (url.pathname === '/api/v1/inventory') return { body: page([item(onHand)]) };
      if (url.pathname === '/api/v1/inventory/p1') return { body: item(onHand) };
      if (url.pathname === '/api/v1/inventory/p1/movements') return { body: page([]) };
      if (url.pathname === '/api/v1/inventory/p1/stock/adjustments' && init.method === 'POST') {
        onHand += JSON.parse(String(init.body)).quantityChange;
        return { body: item(onHand) };
      }
      return undefined;
    });
    renderAt('/admin/inventory?product=p1', '/admin/inventory', <InventoryPage />);
    const user = userEvent.setup();
    const form = await screen.findByRole('form', { name: 'Add or remove' });
    await user.type(within(form).getByLabelText('Change by'), '0');
    await user.click(within(form).getByRole('button', { name: 'Adjust' }));
    expect(within(form).getByText('A change can’t be 0.')).toBeInTheDocument();
    await user.clear(within(form).getByLabelText('Change by'));
    await user.type(within(form).getByLabelText('Change by'), '-3');
    await user.type(within(form).getByLabelText('Reason'), 'Damaged');
    await user.click(within(form).getByRole('button', { name: 'Adjust' }));
    const panel = screen.getByTestId('stock-panel');
    await waitFor(() => expect(within(panel).getByText('On hand').nextSibling).toHaveTextContent('7'));
    expect(calls.filter((c) => c.method === 'POST').map((c) => c.body)).toEqual([{ quantityChange: -3, reason: 'Damaged' }]);
    // the shelf count follows the new number
    expect(within(screen.getByRole('form', { name: 'Count the shelf' })).getByLabelText('Counted on hand')).toHaveValue('7');
  });
});

describe('customers', () => {
  const account = (userId: string, firstName: string, role: 'CUSTOMER' | 'ADMIN', accountStatus = 'ACTIVE') => ({
    userId, firstName, lastName: 'Rao', email: `${firstName.toLowerCase()}@example.com`, phoneNumber: '9876543210', role, accountStatus, createdAt: '2026-10-01T05:00:00Z',
  });

  it('can be suspended after a second click, but never your own account', async () => {
    signInAsAdmin();
    const calls = mockApi((url, init) => {
      if (url.pathname === '/api/v1/admin/users') return { body: page([account('u-1', 'Asha', 'CUSTOMER'), account('admin-1', 'Shop', 'ADMIN')]) };
      if (url.pathname === '/api/v1/admin/users/u-1/status' && init.method === 'PUT') return { body: account('u-1', 'Asha', 'CUSTOMER', 'SUSPENDED') };
      return undefined;
    });
    renderAt('/admin/customers?role=ALL', '/admin/customers', <CustomersPage />);
    const user = userEvent.setup();
    const [asha, me] = await screen.findAllByTestId('customer-row');
    expect(within(me).queryByRole('button', { name: 'Suspend' })).not.toBeInTheDocument();
    await user.click(within(asha).getByRole('button', { name: 'Suspend' }));
    expect(calls.filter((c) => c.method === 'PUT')).toHaveLength(0); // not yet: it asks first
    await user.click(within(asha).getByRole('button', { name: 'Suspend' }));
    await waitFor(() => expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({ status: 'SUSPENDED' }));
  });
});

describe('the dashboard', () => {
  it('shows the period’s sales, and the chart as a table on request', async () => {
    signInAsAdmin();
    const calls = mockApi((url) => {
      switch (url.pathname) {
        case '/api/v1/dashboard/summary':
          return {
            body: {
              from: '2026-10-24', to: '2026-10-30', currency: 'INR',
              sales: { grossSales: 12000, refunds: 1500, netSales: 10500, paidOrders: 6, averageOrderValue: 2000, unitsSold: 9, previousPeriodNetSales: 7000, netSalesChangePercent: 50 },
              orders: { placed: 8, paid: 6, cancelledBeforePayment: 1, cancelledAfterPayment: 1, shipped: 4, delivered: 3 },
              payments: { succeeded: 6, failed: 2, successRatePercent: 75, refunds: 1 },
              customers: { buyers: 5, newBuyers: 2 },
              workload: { ordersToShip: 2, ordersInTransit: 1, lowStockProducts: 1, deliveryFailuresLast7Days: 0 },
            },
          };
        case '/api/v1/dashboard/sales/daily':
          return { body: [{ date: '2026-10-29', paidOrders: 2, grossSales: 4000, refunds: 0, netSales: 4000, unitsSold: 3 }, { date: '2026-10-30', paidOrders: 4, grossSales: 8000, refunds: 1500, netSales: 6500, unitsSold: 6 }] };
        case '/api/v1/dashboard/products/top':
          return { body: [{ productId: 'p1', sku: 'LAMP-01', name: 'Desk Lamp', unitsSold: 5, revenue: 7000, orders: 4 }] };
        case '/api/v1/dashboard/orders/pipeline':
          return { body: [{ status: 'CONFIRMED', orders: 2 }] };
        case '/api/v1/dashboard/inventory/low-stock':
          return { body: [{ productId: 'p1', sku: 'LAMP-01', name: 'Desk Lamp', available: 2, onHand: 2, reserved: 0, threshold: 5, outOfStock: false, updatedAt: '2026-10-30T05:00:00Z' }] };
        case '/api/v1/dashboard/activity':
          return { body: page([{ occurredAt: '2026-10-30T05:00:00Z', kind: 'ORDER_PAID', severity: 'INFO', message: 'Order ORD-1 paid: INR 2,000.00', orderNumber: 'ORD-1' }]) };
      }
      return undefined;
    });
    renderAt('/admin?range=7d', '/admin', <DashboardPage />);
    const user = userEvent.setup();
    expect(await screen.findByTestId('net-sales')).toHaveTextContent('₹10,500');
    expect(screen.getByTestId('paid-orders')).toHaveTextContent('6');
    expect(await screen.findByTestId('low-stock')).toHaveTextContent('Desk Lamp');
    expect(await screen.findByTestId('activity')).toHaveTextContent('Order ORD-1 paid');
    await user.click(await screen.findByRole('button', { name: 'Show as table' }));
    const table = screen.getByRole('table', { name: 'Sales per day' });
    expect(within(table).getAllByRole('row')).toHaveLength(3); // header + 2 days
    expect(within(table).getByText('₹6,500.00')).toBeInTheDocument();
    const summary = calls.find((c) => c.url.pathname === '/api/v1/dashboard/summary')!;
    expect(summary.url.searchParams.get('from')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(summary.url.searchParams.get('to')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});

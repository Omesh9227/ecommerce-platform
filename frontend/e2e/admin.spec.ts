import { expect, test, type Page } from '@playwright/test';
import { ADMIN, Backend, runTag } from './support/backend';

/** Staff sign in through the shop's own sign-in page and land back in the admin area. */
async function signInAsStaff(page: Page, path = '/admin') {
  await page.goto(path);
  await expect(page).toHaveURL(/\/login/);
  await page.getByLabel('Email').fill(ADMIN.email);
  await page.getByLabel('Password').fill(ADMIN.password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('navigation', { name: 'Admin' })).toBeVisible();
}

const nav = (page: Page, name: string) => page.getByRole('navigation', { name: 'Admin' }).getByRole('link', { name, exact: true }).click();

/** Reloads until the text shows: for changes that reach a page through Kafka. */
async function reloadUntil(page: Page, check: () => Promise<void>) {
  await expect(async () => {
    await page.reload();
    await check();
  }).toPass({ timeout: 60_000, intervals: [500, 1000, 2000] });
}

test('staff add a product, stock it, ship its order, moderate its review and manage the buyer', async ({ page, request }) => {
  test.slow(); // a long journey: three times the usual time limit
  const backend = new Backend(request);
  const tag = runTag();
  const name = `Oak Bookshelf ${tag}`;
  await backend.testCategory(); // "Browser tests", where the product goes
  let productId = '';

  await test.step('sign in and see the dashboard', async () => {
    await signInAsStaff(page);
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
    await expect(page.getByTestId('net-sales')).toContainText('₹');
    await expect(page.getByRole('heading', { name: 'Orders right now' })).toBeVisible();
  });

  await test.step('add, rename and delete a category', async () => {
    await nav(page, 'Categories');
    const add = page.getByRole('form', { name: 'Add a category' });
    await add.getByLabel('Category name').fill(`Shelf ${tag}`);
    await add.getByLabel('Inside').selectOption({ label: 'Inside Browser tests' });
    await add.getByRole('button', { name: 'Add' }).click();
    const row = page.getByTestId('category-row').filter({ hasText: `Shelf ${tag}` });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: 'Edit' }).click();
    const edit = page.getByRole('form', { name: `Edit Shelf ${tag}` });
    await edit.getByLabel('Category name').fill(`Shelves ${tag}`);
    await edit.getByRole('button', { name: 'Save' }).click();
    const renamed = page.getByTestId('category-row').filter({ hasText: `Shelves ${tag}` });
    await expect(renamed).toBeVisible();
    await renamed.getByRole('button', { name: 'Delete' }).click();
    await renamed.getByRole('button', { name: 'Delete' }).click(); // confirm
    await expect(renamed).toHaveCount(0);
  });

  await test.step('add a product (mistakes are caught first)', async () => {
    await nav(page, 'Products');
    await page.getByRole('link', { name: 'Add product' }).click();
    await page.getByRole('button', { name: 'Add product' }).click();
    await expect(page.getByText('Choose a category')).toBeVisible();
    await page.getByLabel('Name').fill(name);
    await page.getByLabel('SKU').fill(`SHELF ${tag}`); // a space isn't allowed
    await page.getByLabel('Brand (optional)').fill('Cartwheel Test');
    await page.getByLabel('Category').selectOption({ label: 'Browser tests' });
    await page.getByLabel('Price (₹)').fill('4999');
    await page.getByLabel('Discount (%)').fill('10');
    await expect(page.getByText('Customers pay ₹4,499.10')).toBeVisible();
    await page.getByRole('button', { name: 'Add product' }).click();
    await expect(page.getByText('3–64 letters, digits or hyphens')).toBeVisible();
    await page.getByLabel('SKU').fill(`SHELF-${tag}`);
    await page.getByRole('button', { name: 'Add product' }).click();
    await expect(page.getByText('Saved.')).toBeVisible();
    await expect(page).toHaveURL(/\/admin\/products\/[0-9a-f-]{36}$/);
    productId = page.url().split('/').pop()!;
  });

  await test.step('count the stock and set its low-stock warning', async () => {
    await backend.waitForStockItem(productId);
    await page.getByRole('link', { name: 'Set its stock' }).click();
    const panel = page.getByTestId('stock-panel');
    await expect(panel.getByRole('heading', { name })).toBeVisible();
    const count = panel.getByRole('form', { name: 'Count the shelf' });
    await count.getByLabel('Counted on hand').fill('6');
    await count.getByLabel('Reason').fill('First delivery');
    await count.getByRole('button', { name: 'Set on hand' }).click();
    await expect(panel.getByText('First delivery')).toBeVisible();
    const threshold = panel.getByRole('form', { name: 'Low-stock threshold' });
    await threshold.getByLabel('Threshold').fill('4');
    await threshold.getByRole('button', { name: 'Save threshold' }).click();
    await expect(page.getByTestId('stock-row').filter({ hasText: name })).toContainText('6'); // available 6, threshold 4
  });

  await backend.waitUntilBuyable(productId, 3);
  const customer = await backend.customerBuys(productId, 3);
  const orderNumber = customer.orderNumber;

  await test.step('the paid order shows on the dashboard and stock runs low', async () => {
    await nav(page, 'Dashboard');
    await reloadUntil(page, () => expect(page.getByTestId('activity')).toContainText(`Order ${orderNumber} paid`, { timeout: 2000 }));
    await nav(page, 'Inventory');
    await page.getByLabel('Search SKU or name').fill(`SHELF-${tag}`);
    await page.getByRole('button', { name: 'Search' }).click();
    await expect(page).toHaveURL(/q=SHELF/);
    await page.getByLabel('Low stock only').click(); // the box follows the address, which updates a moment later
    await expect(page).toHaveURL(/low=1/);
    await expect(page.getByLabel('Low stock only')).toBeChecked();
    await reloadUntil(page, () => expect(page.getByTestId('stock-row').filter({ hasText: name })).toContainText('Low', { timeout: 2000 }));
  });

  await test.step('find the order and its payment', async () => {
    await nav(page, 'Orders');
    await page.getByRole('tab', { name: 'Paid, to ship' }).click();
    await page.getByTestId('admin-order-row').filter({ hasText: orderNumber }).getByRole('link', { name: orderNumber }).click();
    await expect(page.getByRole('heading', { name: `Order ${orderNumber}` })).toBeVisible();
    await expect(page.getByTestId('admin-order-status')).toHaveText('Paid, preparing');
    await expect(page.getByTestId('admin-payment')).toContainText('VISA ending 4242');
    await expect(page.getByText(name)).toBeVisible();
  });

  await test.step('pack it, ship it and record the carrier scans', async () => {
    await page.getByRole('link', { name: 'Preparing your package' }).click();
    await page.getByRole('button', { name: 'Mark as packed' }).click();
    await expect(page.getByTestId('shipment-status')).toHaveText('Packed');
    const ship = page.getByRole('form', { name: 'Ship' });
    await ship.getByLabel('Carrier').selectOption('Blue Dart');
    await ship.getByRole('button', { name: 'Mark as shipped' }).click();
    await expect(page.getByTestId('shipment-status')).toHaveText('Shipped');
    const scan = page.getByRole('form', { name: 'Carrier scan' });
    await scan.getByLabel('In transit at a hub').check();
    await scan.getByRole('button', { name: 'Save scan' }).click();
    await expect(scan.getByText('Enter the hub where it was scanned.')).toBeVisible(); // a hub is needed
    await scan.getByLabel('Location').fill('Chennai hub');
    await scan.getByRole('button', { name: 'Save scan' }).click();
    await expect(page.getByTestId('shipment-status')).toHaveText('In transit');
    await scan.getByLabel('Out for delivery').check();
    await scan.getByRole('button', { name: 'Save scan' }).click();
    await expect(page.getByTestId('shipment-status')).toHaveText('Out for delivery');
    await scan.getByLabel('Delivered').check();
    await scan.getByRole('button', { name: 'Save scan' }).click();
    await expect(page.getByTestId('shipment-status')).toHaveText('Delivered');
    await expect(page.getByText('Chennai hub')).toBeVisible(); // in the tracking history
  });

  await test.step('the order is delivered', async () => {
    await page.getByRole('link', { name: 'Open the order' }).click();
    await reloadUntil(page, () => expect(page.getByTestId('admin-order-status')).toHaveText('Delivered', { timeout: 2000 }));
  });

  await test.step('hide the buyer’s review, then show it again', async () => {
    await backend.customerReviews(customer, productId, `Great shelf ${tag}`);
    await page.goto(`/admin/reviews?productId=${productId}`);
    const review = page.getByTestId('admin-review').filter({ hasText: `Great shelf ${tag}` });
    await expect(review).toContainText('Visible');
    await review.getByRole('button', { name: 'Hide' }).click();
    await review.getByLabel('Reason for hiding').fill('Mentions a phone number');
    await review.getByRole('button', { name: 'Hide review' }).click();
    await expect(review).toContainText('Hidden');
    await page.getByRole('tab', { name: 'Hidden' }).click();
    await expect(review).toBeVisible();
    await review.getByRole('button', { name: 'Show again' }).click();
    await page.getByRole('tab', { name: 'All' }).click();
    await expect(review).toContainText('Visible');
  });

  await test.step('suspend the buyer, who can no longer sign in, then reactivate them', async () => {
    await nav(page, 'Customers');
    const row = page.getByTestId('customer-row').filter({ hasText: customer.email });
    await row.getByRole('button', { name: 'Suspend' }).click();
    await row.getByRole('button', { name: 'Suspend' }).click(); // confirm
    await expect(row).toContainText('Suspended');
    expect(await backend.signInStatus(customer)).not.toBe(200);
    await row.getByRole('button', { name: 'Reactivate' }).click();
    await expect(row).toContainText('Active');
    expect(await backend.signInStatus(customer)).toBe(200);
    await row.getByRole('link', { name: 'Orders' }).click();
    await expect(page.getByTestId('admin-order-row')).toHaveCount(1);
    await expect(page.getByTestId('admin-order-row')).toContainText(orderNumber);
  });
});

test('staff cancel a paid order and the customer is refunded', async ({ page, request }) => {
  const backend = new Backend(request);
  const productId = await backend.createProduct({ name: `Floor Cushion ${runTag()}`, price: 899, stock: 4 });
  const { orderNumber } = await backend.customerBuys(productId, 1, 'Kabir');

  await signInAsStaff(page, `/admin/orders/${orderNumber}`);
  await expect(page.getByTestId('admin-order-status')).toHaveText('Paid, preparing');
  await page.getByRole('button', { name: 'Cancel order' }).click();
  const form = page.getByRole('form', { name: 'Cancel order' });
  await expect(form).toContainText('the customer gets a full refund');
  await form.getByLabel('Reason, shown to the customer').fill('Damaged in the warehouse');
  await form.getByRole('button', { name: 'Cancel order' }).click();
  await expect(page.getByTestId('admin-order-status')).toHaveText('Cancelled');
  await expect(page.getByRole('status').filter({ hasText: 'Damaged in the warehouse' })).toBeVisible(); // the reason, shown at the top
  await reloadUntil(page, () => expect(page.getByTestId('admin-payment')).toContainText('Refunded', { timeout: 2000 }));
});

test('customers can’t open the admin area', async ({ page, request }) => {
  const backend = new Backend(request);
  await backend.signedInCustomer(page, 'Nisha');
  await page.goto('/');
  await expect(page.getByRole('button', { name: 'Hi, Nisha' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Admin' })).toHaveCount(0);
  await page.goto('/admin/orders');
  await expect(page.getByRole('heading', { name: 'This area is for shop staff' })).toBeVisible();
});

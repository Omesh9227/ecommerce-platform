import { expect, test, type Page } from '@playwright/test';
import { Backend, runTag } from './support/backend';

const ADDRESS = { phone: '9876543210', line1: 'Flat 12B, Green Residency', city: 'Bengaluru', state: 'Karnataka', pin: '560034' };

async function fillAddress(page: Page) {
  await page.getByLabel('Mobile number').fill(ADDRESS.phone);
  await page.getByLabel('Flat, house number, building, street').fill(ADDRESS.line1);
  await page.getByLabel('Town or city').fill(ADDRESS.city);
  await page.getByLabel('State').selectOption(ADDRESS.state);
  await page.getByLabel('PIN code').fill(ADDRESS.pin);
}

async function payByCard(page: Page, number: string) {
  await page.getByLabel('Card number').fill(number);
  await page.getByLabel('Expiry (MM/YY)').fill('1230');
  await page.getByLabel('CVV').fill('123');
  await page.getByRole('button', { name: /^Pay / }).click();
}

test('a new customer finds a product, buys it, follows the parcel and reviews it', async ({ page, request }) => {
  const backend = new Backend(request);
  const tag = runTag();
  const name = `Walnut Lamp ${tag}`;
  const productId = await backend.createProduct({ name, price: 1299, stock: 5 });

  await test.step('register', async () => {
    await page.goto('/');
    await page.getByRole('navigation', { name: 'Your account' }).getByRole('link', { name: 'Sign in' }).click();
    await page.getByRole('link', { name: 'Create an account' }).click();
    await page.getByLabel('First name').fill('Omesh');
    await page.getByLabel('Last name').fill('Kumar');
    await page.getByLabel('Email').fill(`omesh${Date.now()}@example.com`);
    await page.getByLabel('Mobile number').fill('9876543210');
    await page.getByLabel('Password').fill('weak');
    await page.getByRole('button', { name: 'Create account' }).click();
    await expect(page.getByText('Use 8–64 characters with a lowercase')).toBeVisible();
    await page.getByLabel('Password').fill('Password@123');
    await page.getByRole('button', { name: 'Create account' }).click();
    await expect(page.getByRole('button', { name: 'Hi, Omesh' })).toBeVisible(); // signed in
  });

  await test.step('search with a typo, open the product', async () => {
    await page.getByRole('combobox', { name: 'Search products' }).fill(`walnot lamp ${tag}`); // typo
    await page.getByRole('combobox', { name: 'Search products' }).press('Enter');
    await expect(page.getByTestId('corrected')).toContainText(new RegExp(`walnut lamp ${tag}`, 'i'));
    await page.getByRole('link', { name: new RegExp(name) }).first().click();
    await expect(page.getByRole('heading', { name })).toBeVisible();
    await expect(page.getByTestId('stock')).toHaveText('Only 5 left');
  });

  await test.step('check delivery, add 2 to the cart, save to the wishlist', async () => {
    await page.getByLabel('Delivery to PIN code').fill(ADDRESS.pin);
    await page.getByRole('button', { name: 'Check' }).click();
    await expect(page.getByTestId('delivery-estimate')).toContainText('Delivery by');
    await page.getByLabel('Quantity').selectOption('2');
    await page.getByRole('button', { name: 'Add to cart' }).click();
    await expect(page.getByText('Added to your cart.')).toBeVisible();
    await expect(page.getByTestId('cart-count')).toHaveText('2');
    await page.getByRole('button', { name: 'Save to wishlist' }).click();
    await expect(page.getByRole('button', { name: 'Saved to wishlist' })).toBeVisible();
  });

  let orderNumber = '';
  await test.step('check out', async () => {
    await page.getByRole('link', { name: 'Go to cart' }).click();
    await expect(page.getByTestId('cart-line')).toHaveCount(1);
    await expect(page.getByTestId('cart-subtotal').getByLabel('₹2,598.00')).toBeVisible();
    await page.getByRole('link', { name: 'Proceed to checkout' }).click();
    await expect(page.getByLabel('Full name')).toHaveValue('Omesh Kumar'); // from the account
    await page.getByRole('button', { name: 'Place order and pay' }).click();
    await expect(page.getByText('Enter the house number and street')).toBeVisible(); // nothing sent yet
    await fillAddress(page);
    await page.getByRole('button', { name: 'Place order and pay' }).click();
    await expect(page).toHaveURL(/\/orders\/ORD-\w+\/pay$/);
    orderNumber = page.url().split('/orders/')[1].replace('/pay', '');
    await expect(page.getByTestId('order-total').getByLabel('₹2,598.00')).toBeVisible(); // free delivery over ₹499
    await expect(page.getByTestId('cart-count')).toHaveText('0');
  });

  await test.step('a declined card, then a good one', async () => {
    await payByCard(page, '4000 0000 0000 0002');
    await expect(page.getByTestId('payment-error')).toContainText('Your card was declined.');
    await payByCard(page, '4242 4242 4242 4242');
    await expect(page).toHaveURL(new RegExp(`/orders/${orderNumber}$`));
    await expect(page.getByTestId('order-status')).toHaveText('Paid, preparing');
    await expect(page.getByTestId('payment-details')).toContainText('Paid with VISA ending 4242');
    await expect(page.getByTestId('shipment')).toContainText('Preparing your package');
  });

  await test.step('the parcel is shipped and delivered', async () => {
    await backend.shipAndDeliver(orderNumber);
    await expect(page.getByTestId('order-status')).toHaveText('Delivered');
    await expect(page.getByTestId('shipment')).toContainText('Delivered');
    await page.getByTestId('shipment').getByRole('link', { name: /^EC/ }).click();
    await expect(page.getByTestId('tracking')).toContainText('Delivered');
    await expect(page.getByTestId('tracking')).toContainText('Bengaluru, Karnataka');
  });

  await test.step('review it: a verified purchase', async () => {
    await page.goto(`/products/${productId}`);
    await page.getByRole('button', { name: 'Write a review' }).click();
    await page.getByRole('button', { name: '5 stars' }).click();
    await page.getByLabel('Title').fill('Warm light, solid build');
    await page.getByLabel('Your review').fill('Bright enough to read by. Arrived in two days.');
    await page.getByRole('button', { name: 'Post review' }).click();
    await expect(page.getByTestId('review').filter({ hasText: 'Warm light, solid build' })).toBeVisible();
    await expect(page.getByTestId('average-rating')).toHaveText('5.0 out of 5');
    // review-service marks it verified once it has heard of the delivery (Kafka)
    await expect
      .poll(async () => {
        await page.reload();
        const review = page.getByTestId('review').filter({ hasText: 'Warm light, solid build' });
        await review.waitFor();
        return review.getByText('Verified purchase').count();
      })
      .toBe(1);
  });

  await test.step('orders and wishlist', async () => {
    await page.getByRole('navigation', { name: 'Your account' }).getByRole('link', { name: 'Orders' }).click();
    await expect(page.getByTestId('order-row').filter({ hasText: orderNumber })).toContainText('Delivered');
    await page.getByRole('navigation', { name: 'Your account' }).getByRole('link', { name: 'Wishlist' }).click();
    await expect(page.getByTestId('wishlist-item').filter({ hasText: name })).toBeVisible();
    await page.getByTestId('wishlist-item').filter({ hasText: name }).getByRole('button', { name: 'Remove' }).click();
    await expect(page.getByText('Your wishlist is empty')).toBeVisible();
  });
});

test('cancelling a paid order refunds it', async ({ page, request }) => {
  const backend = new Backend(request);
  const name = `Steel Kettle ${runTag()}`;
  const productId = await backend.createProduct({ name, price: 899, stock: 3 });
  await backend.signedInCustomer(page);

  await page.goto(`/products/${productId}`);
  await page.getByRole('button', { name: 'Add to cart' }).click();
  await expect(page.getByTestId('cart-count')).toHaveText('1');
  await page.goto('/checkout');
  await fillAddress(page);
  await page.getByRole('button', { name: 'Place order and pay' }).click();
  await expect(page).toHaveURL(/\/pay$/);
  await page.getByRole('tab', { name: 'UPI' }).click();
  await page.getByLabel('UPI ID').fill('asha@okhdfc');
  await page.getByRole('button', { name: /^Pay / }).click();
  await expect(page.getByTestId('order-status')).toHaveText('Paid, preparing');

  await page.getByRole('button', { name: 'Cancel order' }).click();
  await expect(page.getByText('You’ll get a full refund of ₹899.00.')).toBeVisible();
  await page.getByLabel('Reason (optional)').fill('Ordered by mistake');
  await page.getByRole('form', { name: 'Cancel order' }).getByRole('button', { name: 'Cancel order' }).click();
  await expect(page.getByTestId('order-status')).toHaveText('Cancelled');
  await expect(page.getByText('Ordered by mistake').first()).toBeVisible();
  await expect(page.getByTestId('payment-details')).toContainText('Refunded ₹899.00');
});

test('the cart needs an account, and sign-in comes back to it', async ({ page, request }) => {
  const email = `ravi${Date.now()}@example.com`;
  await request.post('/api/v1/auth/register', {
    data: { firstName: 'Ravi', lastName: 'Iyer', email, phoneNumber: '9876543212', password: 'Password@123' },
  });
  await page.goto('/cart');
  await expect(page).toHaveURL(/\/login$/);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill('Wrong@1234');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByText('That email and password don’t match an account.')).toBeVisible();
  await page.getByLabel('Password').fill('Password@123');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/cart$/);
  await expect(page.getByText('Your cart is empty')).toBeVisible();
});

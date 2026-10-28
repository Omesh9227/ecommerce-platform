import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError, newIdempotencyKey } from '../api/client';
import { auth, cart as cartApi, orders, shipping } from '../api/shop';
import type { Address } from '../api/types';
import { Empty, ErrorNote, Loading } from '../components/Feedback';
import { Field } from '../components/Field';
import { ProductImage } from '../components/ProductImage';
import { INDIAN_STATES, saveAddress, savedAddress } from '../lib/address';
import { money } from '../lib/format';
import { rules, type Errors } from '../lib/validation';

type AddressField = keyof Address;
const EMPTY: Address = { fullName: '', phone: '', line1: '', line2: '', city: '', state: '', postalCode: '' };

export function validateAddress(a: Address): Errors<AddressField> {
  const errors: Errors<AddressField> = {};
  if (!a.fullName.trim()) errors.fullName = 'Enter the name of the person receiving the order';
  if (!rules.mobile.test(a.phone)) errors.phone = 'Enter a 10-digit mobile number starting with 6–9';
  if (!a.line1.trim()) errors.line1 = 'Enter the house number and street';
  if (!a.city.trim()) errors.city = 'Enter the town or city';
  if (!a.state) errors.state = 'Choose the state';
  if (!rules.pin.test(a.postalCode)) errors.postalCode = 'Enter a 6-digit PIN code';
  return errors;
}

export function CheckoutPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const cart = useQuery({ queryKey: ['cart'], queryFn: cartApi.get, meta: { private: true } });
  const profile = useQuery({ queryKey: ['me'], queryFn: auth.me, meta: { private: true } });
  const [address, setAddress] = useState<Address>(() => ({ ...EMPTY, ...savedAddress() }));
  const [errors, setErrors] = useState<Errors<AddressField>>({});
  const keyRef = useRef(newIdempotencyKey());

  // Fill name and phone from the account when there's no saved address
  useEffect(() => {
    const p = profile.data;
    if (!p) return;
    setAddress((a) => ({
      ...a,
      fullName: a.fullName || `${p.firstName} ${p.lastName}`,
      phone: a.phone || (rules.mobile.test(p.phoneNumber) ? p.phoneNumber : ''),
    }));
  }, [profile.data]);

  const estimate = useQuery({
    queryKey: ['estimate', address.postalCode],
    queryFn: () => shipping.estimate(address.postalCode),
    enabled: rules.pin.test(address.postalCode),
    retry: false,
  });

  const placeOrder = useMutation({
    mutationFn: () => orders.checkout(trimmed(address), undefined, keyRef.current),
    onSuccess: (order) => {
      saveAddress(trimmed(address));
      queryClient.invalidateQueries({ queryKey: ['cart'] });
      queryClient.setQueryData(['order', order.orderNumber], order);
      navigate(order.status === 'PENDING_PAYMENT' ? `/orders/${order.orderNumber}/pay` : `/orders/${order.orderNumber}`, { replace: true });
    },
    onError: (error) => {
      // Retrying after a lost connection must not create a second order: keep the key then.
      // After a clear answer (stock, prices, validation) the next attempt is a new order.
      const status = error instanceof ApiError ? error.status : 0;
      if (status > 0 && status < 500) keyRef.current = newIdempotencyKey();
      if (error instanceof ApiError && Object.keys(error.fieldErrors).length) {
        setErrors(
          Object.fromEntries(Object.entries(error.fieldErrors).map(([k, v]) => [k.replace('shippingAddress.', ''), v])) as Errors<AddressField>,
        );
      }
      queryClient.invalidateQueries({ queryKey: ['cart'] });
    },
  });

  function set(field: AddressField, value: string) {
    setAddress((a) => ({ ...a, [field]: value }));
    if (errors[field]) setErrors((e) => ({ ...e, [field]: undefined }));
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    const found = validateAddress(address);
    setErrors(found);
    if (Object.keys(found).length === 0) placeOrder.mutate();
  }

  if (cart.isLoading) return <Loading />;
  if (cart.isError) return <ErrorNote error={cart.error} retry={() => cart.refetch()} />;
  const c = cart.data!;
  if (c.items.length === 0 && !placeOrder.isPending) {
    return (
      <Empty title="Your cart is empty">
        <Link to="/search" className="btn-link">
          Find something to buy
        </Link>
      </Empty>
    );
  }

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-8 lg:grid-cols-[1fr_22rem]">
      <section>
        <h1 className="text-3xl font-bold">Checkout</h1>
        <h2 className="mt-6 text-xl font-bold">Delivery address</h2>
        <div className="mt-3 grid gap-4 sm:grid-cols-2">
          <Field label="Full name" autoComplete="name" value={address.fullName} error={errors.fullName} onChange={(e) => set('fullName', e.target.value)} />
          <Field
            label="Mobile number"
            autoComplete="tel-national"
            inputMode="numeric"
            maxLength={10}
            value={address.phone}
            error={errors.phone}
            hint="For the delivery agent"
            onChange={(e) => set('phone', e.target.value.replace(/\D/g, ''))}
          />
          <Field
            className="sm:col-span-2"
            label="Flat, house number, building, street"
            autoComplete="address-line1"
            value={address.line1}
            error={errors.line1}
            onChange={(e) => set('line1', e.target.value)}
          />
          <Field
            className="sm:col-span-2"
            label="Area, landmark (optional)"
            autoComplete="address-line2"
            value={address.line2 ?? ''}
            onChange={(e) => set('line2', e.target.value)}
          />
          <Field label="Town or city" autoComplete="address-level2" value={address.city} error={errors.city} onChange={(e) => set('city', e.target.value)} />
          <div>
            <label htmlFor="state" className="mb-1 block text-sm font-semibold">
              State
            </label>
            <select
              id="state"
              className="field"
              autoComplete="address-level1"
              value={address.state}
              aria-invalid={errors.state ? true : undefined}
              onChange={(e) => set('state', e.target.value)}
            >
              <option value="">Choose…</option>
              {INDIAN_STATES.map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
            {errors.state && <p className="mt-1 text-sm text-chili">{errors.state}</p>}
          </div>
          <Field
            label="PIN code"
            autoComplete="postal-code"
            inputMode="numeric"
            maxLength={6}
            value={address.postalCode}
            error={errors.postalCode}
            hint={estimate.data?.message}
            onChange={(e) => set('postalCode', e.target.value.replace(/\D/g, ''))}
          />
        </div>
      </section>

      <aside className="panel h-fit p-5" aria-label="Your order">
        <h2 className="text-lg font-bold">Your order</h2>
        <ul className="mt-3 space-y-3">
          {c.items.map((item) => (
            <li key={item.productId} className="flex gap-3 text-sm">
              <div className="h-12 w-12 shrink-0 overflow-hidden rounded bg-mist">
                <ProductImage src={item.imageUrl} name={item.name} className="text-sm" />
              </div>
              <div className="flex-1">
                <p className="line-clamp-2">{item.name}</p>
                <p className="text-ink-soft">
                  {item.quantity} × {money(item.unitPrice, c.currency)}
                </p>
              </div>
            </li>
          ))}
        </ul>
        <dl className="mt-4 space-y-1 border-t border-line pt-3 text-sm">
          <div className="flex justify-between">
            <dt>Items ({c.totalQuantity})</dt>
            <dd>{money(c.subtotal, c.currency)}</dd>
          </div>
          <div className="flex justify-between text-ink-soft">
            <dt>Delivery</dt>
            <dd>shown on the next step</dd>
          </div>
        </dl>
        {placeOrder.isError && (
          <div className="mt-4">
            <ErrorNote error={placeOrder.error} />
          </div>
        )}
        <button type="submit" className="btn-buy mt-4 w-full py-3 text-base" disabled={placeOrder.isPending || !c.checkoutReady}>
          {placeOrder.isPending ? 'Placing your order…' : 'Place order and pay'}
        </button>
        {!c.checkoutReady && (
          <p className="mt-2 text-sm text-chili">
            Some items need attention. <Link to="/cart" className="underline">Review your cart</Link>
          </p>
        )}
        <p className="mt-3 text-xs text-ink-soft">Your items are reserved for you while you pay.</p>
      </aside>
    </form>
  );
}

function trimmed(a: Address): Address {
  return {
    fullName: a.fullName.trim(),
    phone: a.phone.trim(),
    line1: a.line1.trim(),
    line2: a.line2?.trim() || undefined,
    city: a.city.trim(),
    state: a.state,
    postalCode: a.postalCode.trim(),
  };
}

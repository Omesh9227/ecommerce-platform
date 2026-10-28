import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { isApiError } from '../api/client';
import { orders, payments } from '../api/shop';
import type { Order, PayRequest } from '../api/types';
import { ErrorNote, Loading, Notice } from '../components/Feedback';
import { Field } from '../components/Field';
import { Price } from '../components/Price';
import { money } from '../lib/format';
import { isExpiryPast, luhn, parseExpiry, rules, type Errors } from '../lib/validation';

type Method = 'CARD' | 'UPI';
type CardField = 'number' | 'expiry' | 'cvv' | 'holderName' | 'upiId';

export function validatePayment(method: Method, form: Record<CardField, string>): Errors<CardField> {
  const errors: Errors<CardField> = {};
  if (method === 'UPI') {
    if (!rules.upi.test(form.upiId.trim())) errors.upiId = 'Enter a UPI ID like name@bank';
    return errors;
  }
  if (!luhn(form.number)) errors.number = 'Check the card number';
  const expiry = parseExpiry(form.expiry);
  if (!expiry) errors.expiry = 'Use MM/YY';
  else if (isExpiryPast(expiry.month, expiry.year)) errors.expiry = 'This card has expired';
  if (!rules.cvv.test(form.cvv)) errors.cvv = '3 or 4 digits';
  return errors;
}

export function PayPage() {
  const { orderNumber = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [method, setMethod] = useState<Method>('CARD');
  const [form, setForm] = useState<Record<CardField, string>>({ number: '', expiry: '', cvv: '', holderName: '', upiId: '' });
  const [errors, setErrors] = useState<Errors<CardField>>({});

  const order = useQuery({ queryKey: ['order', orderNumber], queryFn: () => orders.get(orderNumber), meta: { private: true } });
  // payment-service opens the payment when it hears about the order (Kafka): wait for it
  const payment = useQuery({
    queryKey: ['payment', orderNumber],
    queryFn: () => payments.forOrder(orderNumber),
    retry: (count, error) => isApiError(error, 404) && count < 60,
    retryDelay: 1000,
    meta: { private: true },
  });

  const pay = useMutation({
    mutationFn: (request: PayRequest) => payments.pay(orderNumber, request),
    onSuccess: (result) => {
      queryClient.setQueryData(['payment', orderNumber], result);
      queryClient.invalidateQueries({ queryKey: ['order', orderNumber] });
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      navigate(`/orders/${orderNumber}`, { replace: true, state: { justPaid: true } });
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['payment', orderNumber] }),
  });

  if (order.isLoading) return <Loading />;
  if (order.isError) return <ErrorNote error={order.error} retry={() => order.refetch()} />;
  const o = order.data!;

  if (o.status !== 'PENDING_PAYMENT' || payment.data?.status === 'SUCCEEDED') {
    const paid = payment.data?.status === 'SUCCEEDED' || ['CONFIRMED', 'SHIPPED', 'DELIVERED'].includes(o.status);
    return (
      <div className="mx-auto max-w-xl py-10">
        <Notice tone={paid ? 'good' : 'info'}>
          {paid
            ? `Order ${o.orderNumber} is paid.`
            : o.status === 'CANCELLED'
              ? `Order ${o.orderNumber} was cancelled${o.statusReason ? `: ${o.statusReason}` : ''}.`
              : `Order ${o.orderNumber} can’t be paid right now.`}{' '}
          <Link to={`/orders/${o.orderNumber}`} className="font-semibold underline">
            View the order
          </Link>
        </Notice>
      </div>
    );
  }

  function set(field: CardField, value: string) {
    setForm((f) => ({ ...f, [field]: value }));
    if (errors[field]) setErrors((e) => ({ ...e, [field]: undefined }));
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    const found = validatePayment(method, form);
    setErrors(found);
    if (Object.keys(found).length) return;
    if (method === 'UPI') {
      pay.mutate({ method: 'UPI', upiId: form.upiId.trim() });
    } else {
      const expiry = parseExpiry(form.expiry)!;
      pay.mutate({
        method: 'CARD',
        card: {
          number: form.number.replace(/\D/g, ''),
          expiryMonth: expiry.month,
          expiryYear: expiry.year,
          cvv: form.cvv,
          holderName: form.holderName.trim() || undefined,
        },
      });
    }
  }

  const waitingForPayment = payment.isLoading || (payment.isError && isApiError(payment.error, 404));
  const failedAttempts = payment.data?.attempts.filter((a) => a.status === 'FAILED') ?? [];

  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_22rem]">
      <section>
        <h1 className="text-3xl font-bold">Pay for your order</h1>
        <p className="mt-1 text-ink-soft">
          Order <span className="font-semibold text-ink">{o.orderNumber}</span>
        </p>
        <PaymentDeadline order={o} onExpired={() => order.refetch()} />

        <form onSubmit={onSubmit} noValidate className="panel mt-6 p-5">
          <div role="tablist" aria-label="Payment method" className="mb-5 flex gap-2">
            {(['CARD', 'UPI'] as Method[]).map((m) => (
              <button
                key={m}
                type="button"
                role="tab"
                aria-selected={method === m}
                onClick={() => setMethod(m)}
                className={`rounded-md px-4 py-2 text-sm font-semibold ${method === m ? 'bg-ink text-white' : 'bg-mist text-ink hover:bg-mist-deep'}`}
              >
                {m === 'CARD' ? 'Card' : 'UPI'}
              </button>
            ))}
          </div>

          {method === 'CARD' ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <Field
                className="sm:col-span-2"
                label="Card number"
                autoComplete="cc-number"
                inputMode="numeric"
                value={form.number}
                error={errors.number}
                onChange={(e) => set('number', formatCardNumber(e.target.value))}
              />
              <Field
                label="Expiry (MM/YY)"
                autoComplete="cc-exp"
                inputMode="numeric"
                placeholder="12/30"
                value={form.expiry}
                error={errors.expiry}
                onChange={(e) => set('expiry', formatExpiry(e.target.value))}
              />
              <Field
                label="CVV"
                autoComplete="cc-csc"
                inputMode="numeric"
                type="password"
                maxLength={4}
                value={form.cvv}
                error={errors.cvv}
                onChange={(e) => set('cvv', e.target.value.replace(/\D/g, ''))}
              />
              <Field
                className="sm:col-span-2"
                label="Name on card (optional)"
                autoComplete="cc-name"
                value={form.holderName}
                onChange={(e) => set('holderName', e.target.value)}
              />
            </div>
          ) : (
            <Field label="UPI ID" placeholder="name@okhdfc" value={form.upiId} error={errors.upiId} onChange={(e) => set('upiId', e.target.value)} />
          )}

          {pay.isError && (
            <div className="mt-4" data-testid="payment-error">
              <ErrorNote error={pay.error} />
              {isApiError(pay.error, 402) && <p className="mt-2 text-sm text-ink-soft">You haven’t been charged. Try again or use another method.</p>}
            </div>
          )}

          <button type="submit" className="btn-buy mt-5 w-full py-3 text-base" disabled={pay.isPending || waitingForPayment}>
            {waitingForPayment ? 'Getting the payment ready…' : pay.isPending ? 'Paying…' : `Pay ${money(o.totalAmount, o.currency)}`}
          </button>
          {payment.isError && !isApiError(payment.error, 404) && (
            <div className="mt-3">
              <ErrorNote error={payment.error} retry={() => payment.refetch()} />
            </div>
          )}
        </form>

        <details className="mt-4 text-sm text-ink-soft">
          <summary className="cursor-pointer font-semibold text-ink">Payments here are simulated</summary>
          <p className="mt-2 max-w-prose">
            No real money moves. Card 4242 4242 4242 4242 is accepted; 4000 0000 0000 0002 is declined and 4000 0000 0000 9995
            has insufficient funds (any future expiry and any CVV). UPI IDs starting with “fail” are declined.
          </p>
        </details>

        {failedAttempts.length > 0 && (
          <section className="mt-6">
            <h2 className="text-lg font-bold">Earlier attempts</h2>
            <ul className="mt-2 space-y-1 text-sm">
              {failedAttempts.map((a) => (
                <li key={a.attemptId} className="text-ink-soft">
                  {a.method === 'CARD' ? `${a.cardBrand ?? 'Card'} ending ${a.cardLast4}` : `UPI ${a.upiIdMasked}`}: {a.failureReason}
                </li>
              ))}
            </ul>
          </section>
        )}
      </section>

      <aside className="panel h-fit p-5" aria-label="Order total">
        <dl className="space-y-1 text-sm">
          <div className="flex justify-between">
            <dt>Items ({o.itemCount})</dt>
            <dd>{money(o.subtotal, o.currency)}</dd>
          </div>
          <div className="flex justify-between">
            <dt>Delivery</dt>
            <dd>{o.shippingFee > 0 ? money(o.shippingFee, o.currency) : 'Free'}</dd>
          </div>
        </dl>
        <div className="mt-3 border-t border-line pt-3">
          <p className="text-sm font-semibold">Order total</p>
          <div data-testid="order-total">
            <Price amount={o.totalAmount} currency={o.currency} />
          </div>
        </div>
        <div className="mt-4 border-t border-line pt-3 text-sm">
          <p className="font-semibold">Delivering to</p>
          <p className="text-ink-soft">
            {o.shippingAddress.fullName}, {o.shippingAddress.line1}, {o.shippingAddress.city} {o.shippingAddress.postalCode}
          </p>
        </div>
      </aside>
    </div>
  );
}

function PaymentDeadline({ order, onExpired }: { order: Order; onExpired: () => void }) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const due = order.paymentDueAt ? new Date(order.paymentDueAt).getTime() : null;
  const left = due ? Math.max(0, Math.floor((due - now) / 1000)) : null;
  const expiredRef = useRef(onExpired);
  expiredRef.current = onExpired;
  useEffect(() => {
    if (left === 0) expiredRef.current();
  }, [left]);
  if (left === null) return null;
  const minutes = Math.floor(left / 60);
  const seconds = String(left % 60).padStart(2, '0');
  return (
    <p className="mt-3 text-sm">
      {left > 0 ? (
        <>
          Pay within <span className="font-display font-bold">{minutes}:{seconds}</span> to keep your items reserved.
        </>
      ) : (
        <span className="font-semibold text-chili">The time to pay has run out.</span>
      )}
    </p>
  );
}

export function formatCardNumber(value: string): string {
  return value
    .replace(/\D/g, '')
    .slice(0, 19)
    .replace(/(\d{4})(?=\d)/g, '$1 ');
}

export function formatExpiry(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 4);
  return digits.length > 2 ? `${digits.slice(0, 2)}/${digits.slice(2)}` : digits;
}

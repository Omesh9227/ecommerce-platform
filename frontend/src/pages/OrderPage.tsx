import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useLocation, useParams } from 'react-router-dom';
import { isApiError } from '../api/client';
import { orders, payments, shipping } from '../api/shop';
import type { Order, OrderStatus, Payment, Shipment } from '../api/types';
import { ErrorNote, Loading, Notice } from '../components/Feedback';
import { ProductImage } from '../components/ProductImage';
import { OrderStatusBadge, orderStatusLabel, shipmentStatusLabel } from '../components/StatusBadge';
import { date, dateTime, money } from '../lib/format';

const FINAL: OrderStatus[] = ['DELIVERED', 'CANCELLED', 'CHECKOUT_FAILED'];
const CANCELLABLE: OrderStatus[] = ['PENDING_PAYMENT', 'CONFIRMED'];

export function OrderPage() {
  const { orderNumber = '' } = useParams();
  const location = useLocation();
  const justPaid = (location.state as { justPaid?: boolean } | null)?.justPaid ?? false;

  const order = useQuery({
    queryKey: ['order', orderNumber],
    queryFn: () => orders.get(orderNumber),
    // Other services move the order along (payment, shipping): keep it fresh while it's moving
    refetchInterval: (q) => (q.state.data && FINAL.includes(q.state.data.status) ? false : 3000),
    meta: { private: true },
  });
  const status = order.data?.status;
  // Keyed by the order's status too: when the order moves on, its shipment and payment are read again
  const shipment = useQuery({
    queryKey: ['shipment', orderNumber, status],
    queryFn: () => shipping.forOrder(orderNumber),
    enabled: status === 'CONFIRMED' || status === 'SHIPPED' || status === 'DELIVERED' || status === 'CANCELLED',
    refetchInterval: status && !FINAL.includes(status) ? 5000 : false,
    retry: (n, e) => !isApiError(e, 404) && n < 2,
    meta: { private: true },
  });
  const payment = useQuery({
    queryKey: ['payment', orderNumber, status],
    queryFn: () => payments.forOrder(orderNumber),
    enabled: !!status && status !== 'CREATED' && status !== 'CHECKOUT_FAILED',
    // The refund follows the cancellation (payment-service hears about it through Kafka)
    refetchInterval: (q) =>
      status === 'CANCELLED' && (q.state.data?.status === 'SUCCEEDED' || q.state.data?.status === 'PENDING') ? 3000 : false,
    retry: (n, e) => !isApiError(e, 404) && n < 2,
    meta: { private: true },
  });

  if (order.isLoading) return <Loading />;
  if (isApiError(order.error, 404)) {
    return <ErrorNote error={new Error(`There’s no order ${orderNumber} in your account.`)} />;
  }
  if (order.isError) return <ErrorNote error={order.error} retry={() => order.refetch()} />;
  const o = order.data!;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <Link to="/orders" className="text-sm text-ink-soft hover:underline">
            Your orders
          </Link>
          <h1 className="mt-1 text-3xl font-bold">Order {o.orderNumber}</h1>
          <p className="text-sm text-ink-soft">Placed {dateTime(o.placedAt ?? o.createdAt)}</p>
        </div>
        <div className="flex items-center gap-3" data-testid="order-status">
          <OrderStatusBadge status={o.status} />
        </div>
      </div>

      {justPaid && o.status === 'PENDING_PAYMENT' && <Notice tone="good">Payment received. Confirming your order…</Notice>}
      {o.status === 'CONFIRMED' && justPaid && <Notice tone="good">Thank you! Your order is confirmed and being prepared.</Notice>}
      {o.status === 'PENDING_PAYMENT' && !justPaid && (
        <Notice tone="warn">
          This order is waiting for payment.{' '}
          <Link to={`/orders/${o.orderNumber}/pay`} className="font-semibold underline">
            Pay now
          </Link>
        </Notice>
      )}
      {(o.status === 'CANCELLED' || o.status === 'CHECKOUT_FAILED') && o.statusReason && <Notice>{o.statusReason}</Notice>}

      <Progress order={o} />

      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <div className="space-y-6">
          {shipment.data && <ShipmentPanel shipment={shipment.data} />}
          <section className="panel p-5">
            <h2 className="text-lg font-bold">Items</h2>
            <ul className="mt-3 divide-y divide-line">
              {o.items.map((item) => (
                <li key={item.productId} className="flex gap-4 py-3">
                  <Link to={`/products/${item.productId}`} className="h-16 w-16 shrink-0 overflow-hidden rounded bg-mist">
                    <ProductImage src={item.imageUrl} name={item.productName} className="text-base" />
                  </Link>
                  <div className="flex-1 text-sm">
                    <Link to={`/products/${item.productId}`} className="font-semibold hover:underline">
                      {item.productName}
                    </Link>
                    <p className="text-ink-soft">
                      {item.quantity} × {money(item.unitPrice, o.currency)}
                    </p>
                    {o.status === 'DELIVERED' && (
                      <Link to={`/products/${item.productId}#reviews`} className="btn-link mt-1 inline-block">
                        Review this product
                      </Link>
                    )}
                  </div>
                  <span className="font-semibold">{money(item.lineTotal, o.currency)}</span>
                </li>
              ))}
            </ul>
          </section>
          <Timeline order={o} />
        </div>

        <div className="space-y-6">
          <section className="panel p-5 text-sm">
            <h2 className="text-lg font-bold">Payment</h2>
            <dl className="mt-3 space-y-1">
              <Row label="Items" value={money(o.subtotal, o.currency)} />
              <Row label="Delivery" value={o.shippingFee > 0 ? money(o.shippingFee, o.currency) : 'Free'} />
              <div className="flex justify-between border-t border-line pt-2 font-display text-base font-bold">
                <dt>Total</dt>
                <dd>{money(o.totalAmount, o.currency)}</dd>
              </div>
            </dl>
            {payment.data && <PaymentDetails payment={payment.data} />}
          </section>
          <section className="panel p-5 text-sm">
            <h2 className="text-lg font-bold">Delivery address</h2>
            <address className="mt-2 not-italic leading-relaxed text-ink-soft">
              <span className="font-semibold text-ink">{o.shippingAddress.fullName}</span>
              <br />
              {o.shippingAddress.line1}
              {o.shippingAddress.line2 && (
                <>
                  <br />
                  {o.shippingAddress.line2}
                </>
              )}
              <br />
              {o.shippingAddress.city}, {o.shippingAddress.state} {o.shippingAddress.postalCode}
              <br />
              Phone {o.shippingAddress.phone}
            </address>
          </section>
          {CANCELLABLE.includes(o.status) && <CancelOrder order={o} />}
        </div>
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between">
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

/** Placed → Paid → Shipped → Delivered: where the order is in its journey. */
function Progress({ order }: { order: Order }) {
  if (order.status === 'CANCELLED' || order.status === 'CHECKOUT_FAILED') return null;
  const steps: [string, string | null][] = [
    ['Placed', order.placedAt],
    ['Paid', order.confirmedAt],
    ['Shipped', order.shippedAt],
    ['Delivered', order.deliveredAt],
  ];
  const reached = steps.filter(([, at]) => at).length;
  return (
    <ol className="grid grid-cols-4 gap-2" aria-label="Order progress">
      {steps.map(([label, at], i) => (
        <li key={label} aria-current={i === reached - 1 ? 'step' : undefined}>
          <div className={`h-1.5 rounded-full ${at ? 'bg-leaf' : 'bg-mist-deep'}`} />
          <p className={`mt-2 text-sm font-semibold ${at ? 'text-ink' : 'text-ink-faint'}`}>{label}</p>
          <p className="text-xs text-ink-soft">
            {at ? dateTime(at) : label === 'Delivered' && order.estimatedDeliveryDate ? `Expected ${date(order.estimatedDeliveryDate)}` : ''}
          </p>
        </li>
      ))}
    </ol>
  );
}

function ShipmentPanel({ shipment }: { shipment: Shipment }) {
  const latestFirst = [...shipment.tracking].reverse();
  return (
    <section className="panel p-5" data-testid="shipment">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-lg font-bold">{shipmentStatusLabel(shipment.status)}</h2>
        {shipment.estimatedDeliveryDate && shipment.status !== 'DELIVERED' && shipment.status !== 'CANCELLED' && (
          <p className="text-sm">
            Arriving by <span className="font-semibold">{date(shipment.estimatedDeliveryDate)}</span>
          </p>
        )}
      </div>
      {shipment.trackingNumber && (
        <p className="mt-1 text-sm text-ink-soft">
          {shipment.carrier}, tracking number{' '}
          <Link to={`/track/${shipment.trackingNumber}`} className="font-semibold text-ink underline">
            {shipment.trackingNumber}
          </Link>
        </p>
      )}
      {shipment.statusReason && <p className="mt-2 text-sm text-chili">{shipment.statusReason}</p>}
      <TrackingList events={latestFirst} />
    </section>
  );
}

export function TrackingList({ events }: { events: { status: string; description: string; location?: string; occurredAt: string }[] }) {
  if (events.length === 0) return null;
  return (
    <ol className="mt-4 border-l-2 border-line pl-4">
      {events.map((e, i) => (
        <li key={`${e.occurredAt}-${i}`} className="relative pb-4 last:pb-0">
          <span className={`absolute -left-[1.4rem] top-1 h-3 w-3 rounded-full ${i === 0 ? 'bg-leaf' : 'bg-line'}`} aria-hidden />
          <p className="text-sm font-semibold">{e.description}</p>
          <p className="text-xs text-ink-soft">
            {dateTime(e.occurredAt)}
            {e.location ? `, ${e.location}` : ''}
          </p>
        </li>
      ))}
    </ol>
  );
}

function PaymentDetails({ payment }: { payment: Payment }) {
  const how =
    payment.method === 'CARD' ? `${payment.cardBrand ?? 'Card'} ending ${payment.cardLast4}` : payment.method === 'UPI' ? `UPI ${payment.upiIdMasked}` : null;
  return (
    <div className="mt-3 border-t border-line pt-3" data-testid="payment-details">
      {payment.status === 'SUCCEEDED' && <p>Paid{how ? ` with ${how}` : ''} on {dateTime(payment.paidAt)}</p>}
      {payment.status === 'REFUNDED' && (
        <p className="font-semibold text-leaf">
          Refunded {money(payment.amount, payment.currency)} on {dateTime(payment.refundedAt)}
          {how ? ` to ${how}` : ''}
        </p>
      )}
      {payment.status === 'PENDING' && <p className="text-ink-soft">Not paid yet</p>}
      {payment.status === 'CANCELLED' && <p className="text-ink-soft">No payment was taken</p>}
    </div>
  );
}

function Timeline({ order }: { order: Order }) {
  if (!order.timeline?.length) return null;
  return (
    <section className="panel p-5">
      <h2 className="text-lg font-bold">History</h2>
      <ol className="mt-3 space-y-2 text-sm">
        {[...order.timeline].reverse().map((t, i) => (
          <li key={`${t.changedAt}-${i}`} className="flex flex-wrap gap-x-3">
            <span className="w-44 shrink-0 text-ink-soft">{dateTime(t.changedAt)}</span>
            <span>
              <span className="font-semibold">{orderStatusLabel(t.toStatus)}</span>
              {t.note ? `: ${t.note}` : ''}
            </span>
          </li>
        ))}
      </ol>
    </section>
  );
}

function CancelOrder({ order }: { order: Order }) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');
  const cancel = useMutation({
    mutationFn: () => orders.cancel(order.orderNumber, reason.trim() || undefined),
    onSuccess: (updated) => {
      queryClient.setQueryData(['order', order.orderNumber], updated);
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      queryClient.invalidateQueries({ queryKey: ['payment', order.orderNumber] }); // all statuses
      setOpen(false);
    },
  });
  const paid = order.status === 'CONFIRMED';

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    cancel.mutate();
  }

  if (!open) {
    return (
      <button type="button" className="btn-quiet w-full" onClick={() => setOpen(true)}>
        Cancel order
      </button>
    );
  }
  return (
    <form onSubmit={onSubmit} className="panel space-y-3 p-5 text-sm" aria-label="Cancel order">
      <p className="font-semibold">Cancel this order?</p>
      <p className="text-ink-soft">{paid ? `You’ll get a full refund of ${money(order.totalAmount, order.currency)}.` : 'Nothing has been paid.'}</p>
      <label htmlFor="cancel-reason" className="block font-semibold">
        Reason (optional)
      </label>
      <input id="cancel-reason" className="field" maxLength={200} value={reason} onChange={(e) => setReason(e.target.value)} />
      {cancel.isError && <ErrorNote error={cancel.error} />}
      <div className="flex gap-2">
        <button type="submit" className="btn-dark" disabled={cancel.isPending}>
          {cancel.isPending ? 'Cancelling…' : 'Cancel order'}
        </button>
        <button type="button" className="btn-quiet" onClick={() => setOpen(false)}>
          Keep order
        </button>
      </div>
    </form>
  );
}

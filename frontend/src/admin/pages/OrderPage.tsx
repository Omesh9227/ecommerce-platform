import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { adminOrders, adminShipments } from '../../api/admin';
import { isApiError } from '../../api/client';
import { orders, payments } from '../../api/shop';
import { ErrorNote, Loading, Notice } from '../../components/Feedback';
import { OrderStatusBadge, orderStatusLabel, shipmentStatusLabel } from '../../components/StatusBadge';
import { date, dateTime, money } from '../../lib/format';
import { PageHead, Pill, shortId } from '../ui';

export function OrderPage() {
  const { orderNumber = '' } = useParams();
  const order = useQuery({ queryKey: ['admin-order', orderNumber], queryFn: () => orders.get(orderNumber), meta: { private: true } });
  const payment = useQuery({
    queryKey: ['admin-order-payment', orderNumber, order.data?.status],
    queryFn: () => payments.forOrder(orderNumber),
    enabled: !!order.data,
    retry: false,
    meta: { private: true },
  });
  const shipment = useQuery({
    queryKey: ['admin-order-shipment', orderNumber, order.data?.status],
    queryFn: async () => (await adminShipments.list(0, undefined, orderNumber)).content[0] ?? null,
    enabled: !!order.data,
    meta: { private: true },
  });
  const emails = useQuery({
    queryKey: ['admin-order-emails', orderNumber, order.data?.status],
    queryFn: () => adminOrders.emails(orderNumber),
    enabled: !!order.data,
    retry: false,
    meta: { private: true },
  });

  if (order.isLoading) return <Loading />;
  if (isApiError(order.error, 404)) return <ErrorNote error={new Error(`There’s no order ${orderNumber}.`)} />;
  if (order.isError) return <ErrorNote error={order.error} retry={() => order.refetch()} />;
  const o = order.data!;
  const a = o.shippingAddress;

  return (
    <div className="space-y-5">
      <Link to="/admin/orders" className="text-sm text-ink-soft hover:underline">
        All orders
      </Link>
      <PageHead title={`Order ${o.orderNumber}`} sub={`Placed ${dateTime(o.placedAt ?? o.createdAt)}`}>
        <span data-testid="admin-order-status">
          <OrderStatusBadge status={o.status} />
        </span>
      </PageHead>
      {o.statusReason && <Notice>{o.statusReason}</Notice>}

      <div className="grid gap-5 lg:grid-cols-[1fr_22rem]">
        <div className="space-y-5">
          <section className="panel p-5">
            <h2 className="text-lg font-bold">Items</h2>
            <ul className="mt-3 divide-y divide-line text-sm">
              {o.items.map((i) => (
                <li key={i.productId} className="flex justify-between gap-3 py-2">
                  <span>
                    <Link to={`/admin/products/${i.productId}`} className="font-medium hover:underline">
                      {i.productName}
                    </Link>
                    <span className="text-ink-soft">
                      {' '}
                      {i.quantity} × {money(i.unitPrice, o.currency)}
                    </span>
                  </span>
                  <span className="font-semibold">{money(i.lineTotal, o.currency)}</span>
                </li>
              ))}
            </ul>
            <dl className="mt-3 space-y-1 border-t border-line pt-3 text-sm">
              <div className="flex justify-between">
                <dt>Items</dt>
                <dd>{money(o.subtotal, o.currency)}</dd>
              </div>
              <div className="flex justify-between">
                <dt>Delivery</dt>
                <dd>{o.shippingFee > 0 ? money(o.shippingFee, o.currency) : 'Free'}</dd>
              </div>
              <div className="flex justify-between font-display text-base font-bold">
                <dt>Total</dt>
                <dd>{money(o.totalAmount, o.currency)}</dd>
              </div>
            </dl>
          </section>

          <section className="panel p-5">
            <h2 className="text-lg font-bold">History</h2>
            <ol className="mt-3 space-y-1.5 text-sm">
              {[...o.timeline].reverse().map((t, i) => (
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

          <section className="panel p-5">
            <h2 className="text-lg font-bold">Emails sent</h2>
            {emails.data && emails.data.content.length === 0 && <p className="mt-2 text-sm text-ink-soft">None yet.</p>}
            {emails.isError && <p className="mt-2 text-sm text-ink-soft">Emails can’t be listed right now.</p>}
            <ul className="mt-2 divide-y divide-line text-sm">
              {emails.data?.content.map((e) => (
                <li key={e.id} className="flex flex-wrap justify-between gap-2 py-2">
                  <span>{e.subject}</span>
                  <span className="text-ink-soft">
                    {dateTime(e.sentAt)} {e.status !== 'SENT' && <Pill tone="bad">{e.status}</Pill>}
                  </span>
                </li>
              ))}
            </ul>
          </section>
        </div>

        <div className="space-y-5">
          <section className="panel p-5 text-sm">
            <h2 className="text-lg font-bold">Customer</h2>
            <p className="mt-2">
              <Link to={`/admin/orders?userId=${o.userId}`} className="font-mono text-xs hover:underline" title={o.userId}>
                {shortId(o.userId)}
              </Link>
            </p>
            <address className="mt-2 not-italic leading-relaxed text-ink-soft">
              <span className="font-semibold text-ink">{a.fullName}</span>
              <br />
              {a.line1}
              {a.line2 ? `, ${a.line2}` : ''}
              <br />
              {a.city}, {a.state} {a.postalCode}
              <br />
              Phone {a.phone}
            </address>
          </section>

          <section className="panel p-5 text-sm" data-testid="admin-payment">
            <h2 className="text-lg font-bold">Payment</h2>
            {payment.data ? (
              <div className="mt-2 space-y-1">
                <p>
                  <Pill tone={payment.data.status === 'SUCCEEDED' ? 'good' : payment.data.status === 'REFUNDED' ? 'warn' : 'plain'}>{payment.data.status}</Pill>{' '}
                  {money(payment.data.amount, payment.data.currency)}
                </p>
                {payment.data.paidAt && (
                  <p className="text-ink-soft">
                    Paid {dateTime(payment.data.paidAt)}
                    {payment.data.method === 'CARD' ? `, ${payment.data.cardBrand} ending ${payment.data.cardLast4}` : payment.data.method ? `, UPI ${payment.data.upiIdMasked}` : ''}
                  </p>
                )}
                {payment.data.refundedAt && <p className="text-ink-soft">Refunded {dateTime(payment.data.refundedAt)}</p>}
                {payment.data.attempts.filter((t) => t.status === 'FAILED').length > 0 && (
                  <p className="text-ink-soft">{payment.data.attempts.filter((t) => t.status === 'FAILED').length} declined attempt(s)</p>
                )}
              </div>
            ) : (
              <p className="mt-2 text-ink-soft">No payment yet.</p>
            )}
          </section>

          <section className="panel p-5 text-sm">
            <h2 className="text-lg font-bold">Shipment</h2>
            {shipment.data ? (
              <div className="mt-2 space-y-1">
                <p>
                  <Link to={`/admin/shipments/${shipment.data.id}`} className="font-semibold hover:underline">
                    {shipmentStatusLabel(shipment.data.status)}
                  </Link>
                </p>
                {shipment.data.trackingNumber && (
                  <p className="text-ink-soft">
                    {shipment.data.carrier}, {shipment.data.trackingNumber}
                  </p>
                )}
                {shipment.data.estimatedDeliveryDate && <p className="text-ink-soft">Due {date(shipment.data.estimatedDeliveryDate)}</p>}
              </div>
            ) : (
              <p className="mt-2 text-ink-soft">Created once the order is paid.</p>
            )}
          </section>

          {(o.status === 'PENDING_PAYMENT' || o.status === 'CONFIRMED') && <CancelForm orderNumber={o.orderNumber} paid={o.status === 'CONFIRMED'} />}
        </div>
      </div>
    </div>
  );
}

function CancelForm({ orderNumber, paid }: { orderNumber: string; paid: boolean }) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');
  const cancel = useMutation({
    mutationFn: () => adminOrders.cancel(orderNumber, reason.trim() || undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-order', orderNumber] });
      queryClient.invalidateQueries({ queryKey: ['admin-orders'] });
      setOpen(false);
    },
  });
  if (!open) {
    return (
      <button type="button" className="btn-quiet w-full" onClick={() => setOpen(true)}>
        Cancel order
      </button>
    );
  }
  function onSubmit(e: FormEvent) {
    e.preventDefault();
    cancel.mutate();
  }
  return (
    <form onSubmit={onSubmit} className="panel space-y-3 p-5 text-sm" aria-label="Cancel order">
      <p className="font-semibold">Cancel this order?</p>
      <p className="text-ink-soft">{paid ? 'It’s paid: the customer gets a full refund and the stock goes back on the shelf.' : 'Nothing has been paid; the reserved stock is released.'}</p>
      <label htmlFor="admin-cancel-reason" className="block font-semibold">
        Reason, shown to the customer
      </label>
      <input id="admin-cancel-reason" className="field" maxLength={200} value={reason} onChange={(e) => setReason(e.target.value)} />
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

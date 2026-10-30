import { useState, type ReactNode } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { dashboard, type Range } from '../../api/admin';
import { downloadFile } from '../../api/client';
import { ErrorNote, Loading } from '../../components/Feedback';
import { orderStatusLabel } from '../../components/StatusBadge';
import { dateTime, money } from '../../lib/format';
import { SalesChart, TopBars } from '../charts';
import { PageHead, Pill, useParam } from '../ui';

function isoDay(d: Date) {
  return new Date(d.getTime() - d.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}

function daysBack(n: number): Range {
  const to = new Date();
  const from = new Date();
  from.setDate(from.getDate() - (n - 1));
  return { from: isoDay(from), to: isoDay(to) };
}

const PRESETS: [string, string, number][] = [
  ['today', 'Today', 1],
  ['7d', 'Last 7 days', 7],
  ['30d', 'Last 30 days', 30],
  ['90d', 'Last 90 days', 90],
];

export function DashboardPage() {
  const [preset, setPreset] = useParam('range', '30d');
  const [from, setFrom] = useParam('from');
  const [to, setTo] = useParam('to');
  const [by, setBy] = useState<'revenue' | 'units'>('revenue');
  const [severity, setSeverity] = useState<'' | 'WARNING'>('');
  const [feedPage, setFeedPage] = useState(0);
  const [csvError, setCsvError] = useState<unknown>(null);

  const days = PRESETS.find(([key]) => key === preset)?.[2];
  const range: Range = preset === 'custom' ? { from: from || undefined, to: to || undefined } : daysBack(days ?? 30);
  const key = [range.from, range.to];

  const summary = useQuery({ queryKey: ['dash', 'summary', ...key], queryFn: () => dashboard.summary(range), placeholderData: keepPreviousData, meta: { private: true } });
  const daily = useQuery({ queryKey: ['dash', 'daily', ...key], queryFn: () => dashboard.daily(range), placeholderData: keepPreviousData, meta: { private: true } });
  const top = useQuery({ queryKey: ['dash', 'top', by, ...key], queryFn: () => dashboard.top(range, by, 8), placeholderData: keepPreviousData, meta: { private: true } });
  const pipeline = useQuery({ queryKey: ['dash', 'pipeline'], queryFn: dashboard.pipeline, meta: { private: true } });
  const lowStock = useQuery({ queryKey: ['dash', 'low-stock'], queryFn: () => dashboard.lowStock(8), meta: { private: true } });
  const feed = useQuery({
    queryKey: ['dash', 'activity', severity, feedPage],
    queryFn: () => dashboard.activity(severity, feedPage, 12),
    placeholderData: keepPreviousData,
    meta: { private: true },
  });

  const s = summary.data;
  const currency = s?.currency ?? 'INR';

  return (
    <div className="space-y-6">
      <PageHead title="Dashboard" sub={s ? `${dateLabel(s.from)} to ${dateLabel(s.to)}` : undefined}>
        <label className="flex items-center gap-2 text-sm">
          <span className="text-ink-soft">Period</span>
          <select className="field w-auto py-1.5" value={preset} onChange={(e) => setPreset(e.target.value)}>
            {PRESETS.map(([k, label]) => (
              <option key={k} value={k}>
                {label}
              </option>
            ))}
            <option value="custom">Choose dates</option>
          </select>
        </label>
        {preset === 'custom' && (
          <>
            <input type="date" aria-label="From" className="field w-auto py-1.5" value={from} onChange={(e) => setFrom(e.target.value)} />
            <input type="date" aria-label="To" className="field w-auto py-1.5" value={to} onChange={(e) => setTo(e.target.value)} />
          </>
        )}
      </PageHead>

      {summary.isError && <ErrorNote error={summary.error} retry={() => summary.refetch()} />}
      {summary.isLoading && <Loading />}

      {s && (
        <>
          <section aria-label="Waiting now" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Waiting to="/admin/shipments?status=PENDING" label="Orders to ship" value={s.workload.ordersToShip} note={s.workload.oldestToShipPaidAt ? `Oldest paid ${dateTime(s.workload.oldestToShipPaidAt)}` : 'Nothing waiting'} />
            <Waiting to="/admin/shipments?status=SHIPPED" label="Parcels on the way" value={s.workload.ordersInTransit} />
            <Waiting to="/admin/inventory?low=1" label="Low on stock" value={s.workload.lowStockProducts} warn={s.workload.lowStockProducts > 0} />
            <Waiting to="/admin/shipments?status=DELIVERY_FAILED" label="Missed deliveries, 7 days" value={s.workload.deliveryFailuresLast7Days} warn={s.workload.deliveryFailuresLast7Days > 0} />
          </section>

          <section className="panel p-5" aria-label="Sales">
            <div className="flex flex-wrap items-end justify-between gap-4">
              <div>
                <h2 className="font-sans text-sm font-semibold text-ink-soft">Net sales</h2>
                <p className="font-display text-4xl font-extrabold" data-testid="net-sales">
                  {money(s.sales.netSales, currency)}
                </p>
                <p className="mt-1 text-sm">
                  {s.sales.netSalesChangePercent !== undefined ? (
                    <span className={s.sales.netSalesChangePercent >= 0 ? 'font-semibold text-leaf' : 'font-semibold text-chili'}>
                      {s.sales.netSalesChangePercent >= 0 ? 'Up' : 'Down'} {Math.abs(s.sales.netSalesChangePercent)}%
                    </span>
                  ) : (
                    <span className="text-ink-soft">No sales in the period before</span>
                  )}
                  {s.sales.netSalesChangePercent !== undefined && (
                    <span className="text-ink-soft"> vs {money(s.sales.previousPeriodNetSales, currency)} the period before</span>
                  )}
                </p>
              </div>
              <dl className="grid grid-cols-2 gap-x-8 gap-y-2 text-sm sm:grid-cols-5">
                <Figure label="Gross sales" value={money(s.sales.grossSales, currency)} />
                <Figure label="Refunds" value={money(s.sales.refunds, currency)} />
                <Figure label="Paid orders" value={s.sales.paidOrders} testId="paid-orders" />
                <Figure label="Average order" value={s.sales.averageOrderValue !== undefined ? money(s.sales.averageOrderValue, currency) : '—'} />
                <Figure label="Units sold" value={s.sales.unitsSold} />
              </dl>
            </div>
            <div className="mt-5 border-t border-line pt-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="mt-0 text-base">Net sales per day</h3>
                <button
                  type="button"
                  className="btn-quiet px-3 py-1.5"
                  onClick={() => downloadFile(dashboard.csvUrl({ from: s.from, to: s.to }), `sales-${s.from}-to-${s.to}.csv`).catch(setCsvError)}
                >
                  Download CSV
                </button>
              </div>
              {csvError ? <ErrorNote error={csvError} /> : null}
              {daily.data && <SalesChart days={daily.data} currency={currency} />}
            </div>
          </section>

          <section className="grid gap-4 md:grid-cols-3" aria-label="Orders, payments, customers">
            <Group title="Orders">
              <Figure label="Placed" value={s.orders.placed} />
              <Figure label="Paid" value={s.orders.paid} />
              <Figure label="Shipped" value={s.orders.shipped} />
              <Figure label="Delivered" value={s.orders.delivered} />
              <Figure label="Cancelled unpaid" value={s.orders.cancelledBeforePayment} />
              <Figure label="Cancelled after paying" value={s.orders.cancelledAfterPayment} />
            </Group>
            <Group title="Payments">
              <Figure label="Succeeded" value={s.payments.succeeded} />
              <Figure label="Failed" value={s.payments.failed} />
              <Figure label="Success rate" value={s.payments.successRatePercent !== undefined ? `${s.payments.successRatePercent}%` : '—'} />
              <Figure label="Refunds" value={s.payments.refunds} />
            </Group>
            <Group title="Customers">
              <Figure label="Buyers" value={s.customers.buyers} />
              <Figure label="New buyers" value={s.customers.newBuyers} />
            </Group>
          </section>
        </>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        <section className="panel p-5" aria-label="Best sellers">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-lg font-bold">Best sellers</h2>
            <select aria-label="Rank by" className="field w-auto py-1" value={by} onChange={(e) => setBy(e.target.value as 'revenue' | 'units')}>
              <option value="revenue">By revenue</option>
              <option value="units">By units</option>
            </select>
          </div>
          {top.data && top.data.length > 0 ? <TopBars items={top.data} by={by} currency={currency} /> : <p className="text-sm text-ink-soft">No sales in this period.</p>}
        </section>

        <section className="panel p-5" aria-label="Orders by status">
          <h2 className="mb-3 text-lg font-bold">Orders right now</h2>
          <ul className="divide-y divide-line text-sm">
            {pipeline.data?.map((p) => (
              <li key={p.status} className="flex items-center justify-between py-2">
                <Link to={`/admin/orders?status=${p.status}`} className="font-medium hover:underline">
                  {orderStatusLabel(p.status)}
                </Link>
                <span className="text-right">
                  <span className="font-display text-lg font-bold">{p.orders}</span>
                  {p.oldestSince && <span className="block text-xs text-ink-soft">oldest {dateTime(p.oldestSince)}</span>}
                </span>
              </li>
            ))}
          </ul>
        </section>

        <section className="panel p-5" aria-label="Low stock">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-lg font-bold">Low stock</h2>
            <Link to="/admin/inventory?low=1" className="btn-link text-sm">
              All stock
            </Link>
          </div>
          {lowStock.data && lowStock.data.length === 0 && <p className="text-sm text-ink-soft">Every product is above its threshold.</p>}
          <ul className="divide-y divide-line text-sm" data-testid="low-stock">
            {lowStock.data?.map((i) => (
              <li key={i.productId} className="flex items-center justify-between gap-3 py-2">
                <Link to={`/admin/inventory?product=${i.productId}`} className="truncate hover:underline">
                  {i.name}
                </Link>
                <span className="shrink-0">
                  {i.outOfStock ? <Pill tone="bad">Out of stock</Pill> : <Pill tone="warn">{i.available} left</Pill>}
                  <span className="ml-2 text-xs text-ink-soft">threshold {i.threshold}</span>
                </span>
              </li>
            ))}
          </ul>
        </section>

        <section className="panel p-5" aria-label="Activity">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-lg font-bold">Activity</h2>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                className="h-4 w-4 accent-ink"
                checked={severity === 'WARNING'}
                onChange={(e) => {
                  setSeverity(e.target.checked ? 'WARNING' : '');
                  setFeedPage(0);
                }}
              />
              Warnings only
            </label>
          </div>
          {feed.data && feed.data.content.length === 0 && <p className="text-sm text-ink-soft">Nothing yet.</p>}
          <ol className="divide-y divide-line text-sm" data-testid="activity">
            {feed.data?.content.map((a, i) => (
              <li key={`${a.occurredAt}-${i}`} className="flex gap-3 py-2">
                <span className="mt-1.5 shrink-0" aria-hidden>
                  <span className={`block h-2 w-2 rounded-full ${a.severity === 'WARNING' ? 'bg-chili' : 'bg-line'}`} />
                </span>
                <div className="min-w-0">
                  <p className={a.severity === 'WARNING' ? 'font-semibold' : ''}>
                    {a.severity === 'WARNING' && <span className="sr-only">Warning: </span>}
                    {a.orderNumber ? (
                      <Link to={`/admin/orders/${a.orderNumber}`} className="hover:underline">
                        {a.message}
                      </Link>
                    ) : (
                      a.message
                    )}
                  </p>
                  <p className="text-xs text-ink-soft">{dateTime(a.occurredAt)}</p>
                </div>
              </li>
            ))}
          </ol>
          {feed.data && feed.data.totalPages > 1 && (
            <div className="mt-3 flex items-center justify-between text-sm">
              <button type="button" className="btn-quiet px-3 py-1" disabled={feedPage === 0} onClick={() => setFeedPage(feedPage - 1)}>
                Newer
              </button>
              <span className="text-ink-soft">
                {feedPage + 1} of {feed.data.totalPages}
              </span>
              <button type="button" className="btn-quiet px-3 py-1" disabled={feed.data.last} onClick={() => setFeedPage(feedPage + 1)}>
                Older
              </button>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}

function dateLabel(iso: string) {
  return new Date(`${iso}T00:00:00`).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

function Waiting({ to, label, value, note, warn }: { to: string; label: string; value: number; note?: string; warn?: boolean }) {
  return (
    <Link to={to} className={`panel block p-4 hover:border-ink ${warn ? 'border-chili/50' : ''}`}>
      <p className="text-sm text-ink-soft">{label}</p>
      <p className={`font-display text-3xl font-extrabold ${warn ? 'text-chili' : ''}`}>{value}</p>
      {note && <p className="mt-1 text-xs text-ink-soft">{note}</p>}
    </Link>
  );
}

function Group({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="panel p-5">
      <h2 className="mb-3 text-lg font-bold">{title}</h2>
      <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">{children}</dl>
    </div>
  );
}

function Figure({ label, value, testId }: { label: string; value: ReactNode; testId?: string }) {
  return (
    <div>
      <dt className="text-ink-soft">{label}</dt>
      <dd className="font-display text-lg font-bold" data-testid={testId}>
        {value}
      </dd>
    </div>
  );
}

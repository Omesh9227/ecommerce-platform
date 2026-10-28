import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { orders } from '../api/shop';
import type { OrderStatus } from '../api/types';
import { Empty, ErrorNote, Loading } from '../components/Feedback';
import { Pager } from '../components/Pager';
import { OrderStatusBadge } from '../components/StatusBadge';
import { dateTime, money, plural } from '../lib/format';

const FILTERS: [OrderStatus | '', string][] = [
  ['', 'All'],
  ['PENDING_PAYMENT', 'Waiting for payment'],
  ['CONFIRMED', 'Preparing'],
  ['SHIPPED', 'On the way'],
  ['DELIVERED', 'Delivered'],
  ['CANCELLED', 'Cancelled'],
];

export function OrdersPage() {
  const [params, setParams] = useSearchParams();
  const status = (params.get('status') ?? '') as OrderStatus | '';
  const page = Number(params.get('page') ?? 0);
  const list = useQuery({
    queryKey: ['orders', status, page],
    queryFn: () => orders.mine(page, status || undefined),
    placeholderData: keepPreviousData,
    meta: { private: true },
  });

  return (
    <div>
      <h1 className="text-3xl font-bold">Your orders</h1>
      <div role="tablist" aria-label="Show" className="mt-4 flex flex-wrap gap-2">
        {FILTERS.map(([value, label]) => (
          <button
            key={value}
            type="button"
            role="tab"
            aria-selected={status === value}
            onClick={() => setParams(value ? { status: value } : {})}
            className={`rounded-full px-3 py-1 text-sm ${status === value ? 'bg-ink text-white' : 'bg-white text-ink hover:bg-mist-deep'}`}
          >
            {label}
          </button>
        ))}
      </div>
      {list.isLoading && <Loading />}
      {list.isError && <ErrorNote error={list.error} retry={() => list.refetch()} />}
      {list.data && list.data.content.length === 0 && (
        <Empty title={status ? 'No orders here' : 'No orders yet'}>
          <Link to="/search" className="btn-link">
            Start shopping
          </Link>
        </Empty>
      )}
      {list.data && list.data.content.length > 0 && (
        <>
          <ul className="mt-6 space-y-3">
            {list.data.content.map((o) => (
              <li key={o.orderNumber}>
                <Link
                  to={`/orders/${o.orderNumber}`}
                  className="panel flex flex-wrap items-center justify-between gap-3 px-5 py-4 hover:border-ink"
                  data-testid="order-row"
                >
                  <div>
                    <p className="font-display font-bold">{o.orderNumber}</p>
                    <p className="text-sm text-ink-soft">
                      {dateTime(o.placedAt ?? o.createdAt)}, {plural(o.itemCount, 'item')}
                    </p>
                  </div>
                  <div className="flex items-center gap-4">
                    <OrderStatusBadge status={o.status} />
                    <span className="font-display text-lg font-bold">{money(o.totalAmount, o.currency)}</span>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
          <Pager
            page={list.data.page}
            totalPages={list.data.totalPages}
            onPage={(p) => setParams({ ...(status ? { status } : {}), page: String(p) })}
          />
        </>
      )}
    </div>
  );
}

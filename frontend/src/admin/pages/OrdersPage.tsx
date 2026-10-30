import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { adminOrders } from '../../api/admin';
import type { OrderStatus } from '../../api/types';
import { ErrorNote, Loading } from '../../components/Feedback';
import { Pager } from '../../components/Pager';
import { OrderStatusBadge } from '../../components/StatusBadge';
import { dateTime, money } from '../../lib/format';
import { PageHead, Table, Tabs, shortId, useParam } from '../ui';

const FILTERS: [OrderStatus | '', string][] = [
  ['', 'All'],
  ['PENDING_PAYMENT', 'Waiting for payment'],
  ['CONFIRMED', 'Paid, to ship'],
  ['SHIPPED', 'Shipped'],
  ['DELIVERED', 'Delivered'],
  ['CANCELLED', 'Cancelled'],
  ['CHECKOUT_FAILED', 'Failed checkouts'],
];

export function OrdersPage() {
  const [status, setStatus] = useParam('status');
  const [userId, setUserId] = useParam('userId');
  const [page, setPage] = useParam('page', '0');
  const list = useQuery({
    queryKey: ['admin-orders', status, userId, page],
    queryFn: () => adminOrders.list(Number(page), (status || undefined) as OrderStatus | undefined, userId || undefined),
    placeholderData: keepPreviousData,
    meta: { private: true },
  });

  return (
    <div>
      <PageHead title="Orders" sub={list.data ? `${list.data.totalElements} orders` : undefined} />
      {userId && (
        <p className="mb-3 text-sm">
          Orders of customer <span className="font-mono">{shortId(userId)}</span>{' '}
          <button type="button" className="btn-link" onClick={() => setUserId('')}>
            Show everyone’s
          </button>
        </p>
      )}
      <Tabs label="Status" value={status as OrderStatus | ''} options={FILTERS} onChange={setStatus} />
      {list.isLoading && <Loading />}
      {list.isError && <ErrorNote error={list.error} retry={() => list.refetch()} />}
      {list.data && (
        <>
          <Table label="Orders" head={['Order', 'Placed', 'Customer', 'Items', 'Total', 'Status']}>
            {list.data.content.map((o) => (
              <tr key={o.orderNumber} data-testid="admin-order-row">
                <td className="px-4 py-2.5">
                  <Link to={`/admin/orders/${o.orderNumber}`} className="font-semibold hover:underline">
                    {o.orderNumber}
                  </Link>
                </td>
                <td className="px-4 py-2.5 text-ink-soft">{dateTime(o.placedAt ?? o.createdAt)}</td>
                <td className="px-4 py-2.5">
                  <Link to={`/admin/orders?userId=${o.userId}`} className="font-mono text-xs hover:underline" title={o.userId}>
                    {shortId(o.userId)}
                  </Link>
                </td>
                <td className="px-4 py-2.5">{o.itemCount}</td>
                <td className="px-4 py-2.5 font-semibold">{money(o.totalAmount, o.currency)}</td>
                <td className="px-4 py-2.5">
                  <OrderStatusBadge status={o.status} />
                </td>
              </tr>
            ))}
          </Table>
          {list.data.content.length === 0 && <p className="mt-4 text-ink-soft">No orders here.</p>}
          <Pager page={list.data.page} totalPages={list.data.totalPages} onPage={(p) => setPage(String(p))} />
        </>
      )}
    </div>
  );
}

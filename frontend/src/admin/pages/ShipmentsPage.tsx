import { useState, type FormEvent } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { adminShipments, CARRIERS, type AdminShipment } from '../../api/admin';
import type { ShipmentStatus } from '../../api/types';
import { ErrorNote, Loading } from '../../components/Feedback';
import { Pager } from '../../components/Pager';
import { shipmentStatusLabel } from '../../components/StatusBadge';
import { date, dateTime } from '../../lib/format';
import { PageHead, Pill, Table, Tabs, useParam } from '../ui';

const FILTERS: [ShipmentStatus | '', string][] = [
  ['PENDING', 'To pack'],
  ['PACKED', 'Packed, to ship'],
  ['SHIPPED', 'Shipped'],
  ['IN_TRANSIT', 'In transit'],
  ['OUT_FOR_DELIVERY', 'Out for delivery'],
  ['DELIVERY_FAILED', 'Missed delivery'],
  ['DELIVERED', 'Delivered'],
  ['', 'All'],
];

function tone(status: ShipmentStatus) {
  if (status === 'DELIVERED') return 'good' as const;
  if (status === 'DELIVERY_FAILED' || status === 'CANCELLED') return 'bad' as const;
  if (status === 'PENDING' || status === 'PACKED') return 'warn' as const;
  return 'plain' as const;
}

/** The warehouse queue, oldest first: pack, ship, then record the carrier's scans. */
export function ShipmentsPage() {
  const [status, setStatus] = useParam('status', 'PENDING');
  const [orderNumber, setOrderNumber] = useParam('order');
  const [page, setPage] = useParam('page', '0');
  const [find, setFind] = useState(orderNumber);
  const list = useQuery({
    queryKey: ['admin-shipments', status, orderNumber, page],
    queryFn: () => adminShipments.list(Number(page), (status || undefined) as ShipmentStatus | undefined, orderNumber || undefined),
    placeholderData: keepPreviousData,
    refetchInterval: 15_000,
    meta: { private: true },
  });

  return (
    <div>
      <PageHead title="Shipments" sub="Oldest first, so nothing waits too long.">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            setOrderNumber(find.trim().toUpperCase());
          }}
          className="flex gap-2"
        >
          <input aria-label="Find by order number" className="field w-56 py-1.5" placeholder="Order number" value={find} onChange={(e) => setFind(e.target.value)} />
          <button type="submit" className="btn-quiet px-3 py-1.5">
            Find
          </button>
        </form>
      </PageHead>
      <Tabs label="Status" value={status as ShipmentStatus | ''} options={FILTERS} onChange={setStatus} />
      {list.isLoading && <Loading />}
      {list.isError && <ErrorNote error={list.error} retry={() => list.refetch()} />}
      {list.data && (
        <>
          <Table label="Shipments" head={['Order', 'Status', 'Destination', 'Items', 'Carrier', 'Due', 'Created']}>
            {list.data.content.map((s) => (
              <tr key={s.id} data-testid="shipment-row">
                <td className="px-4 py-2.5">
                  <Link to={`/admin/shipments/${s.id}`} className="font-semibold hover:underline">
                    {s.orderNumber}
                  </Link>
                </td>
                <td className="px-4 py-2.5">
                  <Pill tone={tone(s.status)}>{shipmentStatusLabel(s.status)}</Pill>
                </td>
                <td className="px-4 py-2.5">
                  {s.city} {s.postalCode}
                </td>
                <td className="px-4 py-2.5">{s.itemCount}</td>
                <td className="px-4 py-2.5">{s.carrier ? `${s.carrier}, ${s.trackingNumber}` : '—'}</td>
                <td className="px-4 py-2.5">{date(s.estimatedDeliveryDate)}</td>
                <td className="px-4 py-2.5 text-ink-soft">{dateTime(s.createdAt)}</td>
              </tr>
            ))}
          </Table>
          {list.data.content.length === 0 && <p className="mt-4 text-ink-soft">No shipments here.</p>}
          <Pager page={list.data.page} totalPages={list.data.totalPages} onPage={(p) => setPage(String(p))} />
        </>
      )}
    </div>
  );
}

export function ShipmentPage() {
  const { id = '' } = useParams();
  const queryClient = useQueryClient();
  const shipment = useQuery({ queryKey: ['admin-shipment', id], queryFn: () => adminShipments.get(id), meta: { private: true } });
  const onDone = (s: AdminShipment) => {
    queryClient.setQueryData(['admin-shipment', id], s);
    queryClient.invalidateQueries({ queryKey: ['admin-shipments'] });
  };

  if (shipment.isLoading) return <Loading />;
  if (shipment.isError) return <ErrorNote error={shipment.error} retry={() => shipment.refetch()} />;
  const s = shipment.data!;
  const a = s.deliveryAddress;
  const onTheWay = ['SHIPPED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERY_FAILED'].includes(s.status);

  return (
    <div className="space-y-5">
      <Link to="/admin/shipments" className="text-sm text-ink-soft hover:underline">
        All shipments
      </Link>
      <PageHead title={`Shipment for ${s.orderNumber}`} sub={<Link to={`/admin/orders/${s.orderNumber}`} className="btn-link">Open the order</Link>}>
        <span data-testid="shipment-status">
          <Pill tone={tone(s.status)}>{shipmentStatusLabel(s.status)}</Pill>
        </span>
      </PageHead>

      <div className="grid gap-5 lg:grid-cols-[1fr_24rem]">
        <div className="space-y-5">
          <section className="panel p-5 text-sm">
            <h2 className="text-lg font-bold">{s.status === 'PENDING' ? 'Pack these' : 'Items'}</h2>
            <ul className="mt-2 divide-y divide-line">
              {s.items?.map((i) => (
                <li key={i.productId} className="flex justify-between py-2">
                  <span>{i.productName}</span>
                  <span className="font-mono text-xs text-ink-soft">
                    {i.sku} × {i.quantity}
                  </span>
                </li>
              ))}
            </ul>
          </section>
          <section className="panel p-5 text-sm">
            <h2 className="text-lg font-bold">Tracking history</h2>
            <ol className="mt-2 space-y-2">
              {[...s.tracking].reverse().map((t, i) => (
                <li key={`${t.occurredAt}-${i}`}>
                  <p className="font-semibold">{t.description}</p>
                  <p className="text-xs text-ink-soft">
                    {dateTime(t.occurredAt)}
                    {t.location ? `, ${t.location}` : ''}
                  </p>
                </li>
              ))}
            </ol>
          </section>
        </div>
        <div className="space-y-5">
          {a && (
            <section className="panel p-5 text-sm">
              <h2 className="text-lg font-bold">Deliver to</h2>
              <address className="mt-2 not-italic leading-relaxed">
                <span className="font-semibold">{a.recipientName}</span>
                <br />
                {a.line1}
                {a.line2 ? `, ${a.line2}` : ''}
                <br />
                {a.city}, {a.state} {a.postalCode}
                <br />
                Phone {a.phone}
              </address>
              {s.estimatedDeliveryDate && <p className="mt-2 text-ink-soft">Promised by {date(s.estimatedDeliveryDate)}</p>}
              {s.carrier && (
                <p className="mt-1 text-ink-soft">
                  {s.carrier}, {s.trackingNumber}
                </p>
              )}
            </section>
          )}
          {s.status === 'PENDING' && <PackAction id={s.id} onDone={onDone} />}
          {s.status === 'PACKED' && <ShipAction id={s.id} onDone={onDone} />}
          {onTheWay && <ScanAction shipment={s} onDone={onDone} />}
          {s.status === 'DELIVERED' && <p className="panel p-5 text-sm text-leaf">Delivered. Nothing left to do.</p>}
          {s.status === 'CANCELLED' && <p className="panel p-5 text-sm">The order was cancelled{s.statusReason ? `: ${s.statusReason}` : ''}.</p>}
        </div>
      </div>
    </div>
  );
}

function PackAction({ id, onDone }: { id: string; onDone: (s: AdminShipment) => void }) {
  const pack = useMutation({ mutationFn: () => adminShipments.pack(id), onSuccess: onDone });
  return (
    <section className="panel space-y-3 p-5 text-sm">
      <h2 className="text-lg font-bold">Next: pack it</h2>
      <button type="button" className="btn-buy w-full" disabled={pack.isPending} onClick={() => pack.mutate()}>
        {pack.isPending ? 'Saving…' : 'Mark as packed'}
      </button>
      {pack.isError && <ErrorNote error={pack.error} />}
    </section>
  );
}

function ShipAction({ id, onDone }: { id: string; onDone: (s: AdminShipment) => void }) {
  const [carrier, setCarrier] = useState(CARRIERS[0]);
  const [tracking, setTracking] = useState('');
  const ship = useMutation({ mutationFn: () => adminShipments.ship(id, carrier, tracking.trim()), onSuccess: onDone });
  function onSubmit(e: FormEvent) {
    e.preventDefault();
    ship.mutate();
  }
  return (
    <form onSubmit={onSubmit} className="panel space-y-3 p-5 text-sm" aria-label="Ship">
      <h2 className="text-lg font-bold">Next: hand it to a carrier</h2>
      <label className="block">
        <span className="mb-1 block font-semibold">Carrier</span>
        <select className="field" value={carrier} onChange={(e) => setCarrier(e.target.value)}>
          {CARRIERS.map((c) => (
            <option key={c}>{c}</option>
          ))}
        </select>
      </label>
      <label className="block">
        <span className="mb-1 block font-semibold">Tracking number (optional)</span>
        <input className="field" value={tracking} onChange={(e) => setTracking(e.target.value)} placeholder="Leave empty to generate one" />
      </label>
      <button type="submit" className="btn-buy w-full" disabled={ship.isPending}>
        {ship.isPending ? 'Saving…' : 'Mark as shipped'}
      </button>
      {ship.isError && <ErrorNote error={ship.error} />}
    </form>
  );
}

const SCANS: [ShipmentStatus, string][] = [
  ['IN_TRANSIT', 'In transit at a hub'],
  ['OUT_FOR_DELIVERY', 'Out for delivery'],
  ['DELIVERY_FAILED', 'Delivery attempt failed'],
  ['DELIVERED', 'Delivered'],
];

function ScanAction({ shipment, onDone }: { shipment: AdminShipment; onDone: (s: AdminShipment) => void }) {
  const [status, setStatus] = useState<ShipmentStatus>(shipment.status === 'OUT_FOR_DELIVERY' ? 'DELIVERED' : shipment.status === 'IN_TRANSIT' ? 'OUT_FOR_DELIVERY' : 'IN_TRANSIT');
  const [location, setLocation] = useState('');
  const [note, setNote] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const scan = useMutation({
    mutationFn: () => adminShipments.scan(shipment.id, status, location.trim(), note.trim()),
    onSuccess: (s) => {
      setLocation('');
      setNote('');
      onDone(s);
    },
  });
  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (status === 'IN_TRANSIT' && !location.trim()) return setProblem('Enter the hub where it was scanned.');
    if (status === 'DELIVERY_FAILED' && !note.trim()) return setProblem('Enter why the delivery failed.');
    setProblem(null);
    scan.mutate();
  }
  return (
    <form onSubmit={onSubmit} className="panel space-y-3 p-5 text-sm" aria-label="Carrier scan">
      <h2 className="text-lg font-bold">Record a carrier scan</h2>
      <fieldset className="space-y-1.5">
        <legend className="mb-1 font-semibold">What happened</legend>
        {SCANS.map(([value, label]) => (
          <label key={value} className="flex items-center gap-2">
            <input type="radio" name="scan" className="accent-ink" checked={status === value} onChange={() => setStatus(value)} />
            {label}
          </label>
        ))}
      </fieldset>
      <label className="block">
        <span className="mb-1 block font-semibold">Location{status === 'IN_TRANSIT' ? '' : ' (optional)'}</span>
        <input className="field" value={location} onChange={(e) => setLocation(e.target.value)} placeholder="e.g. Hyderabad hub" />
      </label>
      {status === 'DELIVERY_FAILED' && (
        <label className="block">
          <span className="mb-1 block font-semibold">Why it failed</span>
          <input className="field" value={note} onChange={(e) => setNote(e.target.value)} placeholder="e.g. Customer not available" />
        </label>
      )}
      {problem && <p className="text-chili">{problem}</p>}
      <button type="submit" className="btn-buy w-full" disabled={scan.isPending}>
        {scan.isPending ? 'Saving…' : 'Save scan'}
      </button>
      {scan.isError && <ErrorNote error={scan.error} />}
    </form>
  );
}

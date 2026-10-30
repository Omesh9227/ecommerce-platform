import { useEffect, useRef, useState, type FormEvent } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminInventory, type StockItem } from '../../api/admin';
import { ErrorNote, Loading } from '../../components/Feedback';
import { Pager } from '../../components/Pager';
import { dateTime } from '../../lib/format';
import { PageHead, Pill, Table, useParam } from '../ui';

const MOVEMENT: Record<string, string> = {
  ADJUSTMENT: 'Adjusted',
  RESTOCK: 'Restocked',
  RESERVE: 'Reserved for an order',
  RELEASE: 'Reservation released',
  SALE: 'Sold',
  RETURN: 'Returned',
};

export function InventoryPage() {
  const [search, setSearch] = useParam('q');
  const [low, setLow] = useParam('low');
  const [selected, setSelected] = useParam('product');
  const [page, setPage] = useParam('page', '0');
  const [text, setText] = useState(search);
  const list = useQuery({
    queryKey: ['admin-stock', search, low, page],
    queryFn: () => adminInventory.list({ search: search || undefined, lowStock: low === '1', page: Number(page) }),
    placeholderData: keepPreviousData,
    meta: { private: true },
  });

  return (
    <div>
      <PageHead title="Inventory" sub="Available = on hand − reserved for unpaid orders." />
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            setSearch(text.trim());
          }}
        >
          <input aria-label="Search SKU or name" className="field w-64 py-1.5" placeholder="SKU or product name" value={text} onChange={(e) => setText(e.target.value)} />
          <button type="submit" className="btn-quiet px-3 py-1.5">
            Search
          </button>
        </form>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" className="h-4 w-4 accent-ink" checked={low === '1'} onChange={(e) => setLow(e.target.checked ? '1' : '')} />
          Low stock only
        </label>
      </div>
      <div className="grid gap-5 xl:grid-cols-[1fr_24rem]">
        <div>
          {list.isLoading && <Loading />}
          {list.isError && <ErrorNote error={list.error} retry={() => list.refetch()} />}
          {list.data && (
            <>
              <Table label="Stock" head={['Product', 'SKU', 'On hand', 'Reserved', 'Available', 'Threshold', '']}>
                {list.data.content.map((i) => (
                  <tr key={i.productId} className={selected === i.productId ? 'bg-marigold-pale' : ''} data-testid="stock-row">
                    <td className="px-4 py-2.5">
                      <button type="button" className="text-left font-semibold hover:underline" onClick={() => setSelected(i.productId)}>
                        {i.productName}
                      </button>
                    </td>
                    <td className="px-4 py-2.5 font-mono text-xs">{i.sku}</td>
                    <td className="px-4 py-2.5">{i.quantityOnHand}</td>
                    <td className="px-4 py-2.5">{i.quantityReserved}</td>
                    <td className="px-4 py-2.5 font-semibold">{i.availableQuantity}</td>
                    <td className="px-4 py-2.5">{i.lowStockThreshold}</td>
                    <td className="px-4 py-2.5">{i.availableQuantity === 0 ? <Pill tone="bad">Out</Pill> : i.lowStock ? <Pill tone="warn">Low</Pill> : null}</td>
                  </tr>
                ))}
              </Table>
              {list.data.content.length === 0 && <p className="mt-4 text-ink-soft">No stock items match.</p>}
              <Pager page={list.data.page} totalPages={list.data.totalPages} onPage={(p) => setPage(String(p))} />
            </>
          )}
        </div>
        {selected && <StockPanel productId={selected} onClose={() => setSelected('')} />}
      </div>
    </div>
  );
}

function StockPanel({ productId, onClose }: { productId: string; onClose: () => void }) {
  const queryClient = useQueryClient();
  const item = useQuery({ queryKey: ['admin-stock-item', productId], queryFn: () => adminInventory.get(productId), meta: { private: true } });
  const panel = useRef<HTMLElement>(null);
  // on a phone the panel sits under the list: bring it into view when a product is picked
  useEffect(() => panel.current?.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' }), [productId, item.isSuccess]);
  const movements = useQuery({ queryKey: ['admin-stock-moves', productId], queryFn: () => adminInventory.movements(productId), meta: { private: true } });
  const refresh = (i: StockItem) => {
    queryClient.setQueryData(['admin-stock-item', productId], i);
    queryClient.invalidateQueries({ queryKey: ['admin-stock'] });
    queryClient.invalidateQueries({ queryKey: ['admin-stock-moves', productId] });
  };
  if (item.isLoading) return <Loading />;
  if (item.isError) return <ErrorNote error={item.error} />;
  const i = item.data!;
  return (
    <aside ref={panel} className="panel h-fit space-y-5 p-5 text-sm" aria-label="Stock of one product" data-testid="stock-panel">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold">{i.productName}</h2>
          <p className="font-mono text-xs text-ink-soft">{i.sku}</p>
        </div>
        <button type="button" className="btn-link" onClick={onClose}>
          Close
        </button>
      </div>
      <dl className="grid grid-cols-3 gap-2 text-center">
        <Stat label="On hand" value={i.quantityOnHand} />
        <Stat label="Reserved" value={i.quantityReserved} />
        <Stat label="Available" value={i.availableQuantity} />
      </dl>
      <NumberAction
        key={`count-${i.quantityOnHand}`}
        title="Count the shelf"
        field="Counted on hand"
        hint="Sets on hand to the number you counted."
        button="Set on hand"
        initial={String(i.quantityOnHand)}
        min={0}
        withReason
        onSave={(n, reason) => adminInventory.setStock(productId, n, reason)}
        onDone={refresh}
      />
      <NumberAction
        title="Add or remove"
        field="Change by"
        hint="Positive adds (a delivery), negative removes (damaged, lost)."
        button="Adjust"
        initial=""
        allowNegative
        withReason
        onSave={(n, reason) => adminInventory.adjust(productId, n, reason)}
        onDone={refresh}
      />
      <NumberAction
        key={`threshold-${i.lowStockThreshold}`}
        title="Low-stock threshold"
        field="Threshold"
        hint="Warn when available falls to this number."
        button="Save threshold"
        initial={String(i.lowStockThreshold)}
        min={0}
        onSave={(n) => adminInventory.threshold(productId, n)}
        onDone={refresh}
      />
      <div>
        <h3 className="mt-0 text-base">Recent changes</h3>
        <ul className="mt-2 divide-y divide-line">
          {movements.data?.content.map((m) => (
            <li key={m.id} className="py-1.5">
              <span className="font-semibold">
                {m.quantityChange > 0 ? '+' : ''}
                {m.quantityChange}
              </span>{' '}
              {MOVEMENT[m.movementType] ?? m.movementType}
              {m.reason ? `: ${m.reason}` : ''}
              {m.reference ? ` (${m.reference})` : ''}
              <span className="block text-xs text-ink-soft">
                {dateTime(m.createdAt)}, on hand {m.quantityOnHandAfter}
              </span>
            </li>
          ))}
        </ul>
      </div>
    </aside>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-md bg-mist p-2">
      <dt className="text-xs text-ink-soft">{label}</dt>
      <dd className="font-display text-xl font-bold">{value}</dd>
    </div>
  );
}

function NumberAction({ title, field, hint, button, initial, min, allowNegative, withReason, onSave, onDone }: {
  title: string;
  field: string;
  hint: string;
  button: string;
  initial: string;
  min?: number;
  allowNegative?: boolean;
  withReason?: boolean;
  onSave: (n: number, reason?: string) => Promise<StockItem>;
  onDone: (i: StockItem) => void;
}) {
  const [value, setValue] = useState(initial);
  const [reason, setReason] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const save = useMutation({
    mutationFn: () => onSave(Number(value), reason.trim() || undefined),
    onSuccess: (i) => {
      if (allowNegative) setValue('');
      setReason('');
      onDone(i);
    },
  });
  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const n = Number(value);
    if (value.trim() === '' || !Number.isInteger(n)) return setProblem('Enter a whole number.');
    if (min !== undefined && n < min) return setProblem(`At least ${min}.`);
    if (allowNegative && n === 0) return setProblem('A change can’t be 0.');
    setProblem(null);
    save.mutate();
  }
  return (
    <form onSubmit={onSubmit} aria-label={title} className="space-y-2 border-t border-line pt-4">
      <h3 className="mt-0 text-base">{title}</h3>
      <p className="text-xs text-ink-soft">{hint}</p>
      <div className="flex gap-2">
        <input aria-label={field} inputMode="numeric" className="field w-28" value={value} onChange={(e) => setValue(e.target.value.replace(allowNegative ? /[^\d-]/g : /\D/g, ''))} />
        {withReason && <input aria-label="Reason" className="field" placeholder="Reason (optional)" maxLength={255} value={reason} onChange={(e) => setReason(e.target.value)} />}
      </div>
      <button type="submit" className="btn-dark px-3 py-1.5" disabled={save.isPending}>
        {save.isPending ? 'Saving…' : button}
      </button>
      {problem && <p className="text-chili">{problem}</p>}
      {save.isError && <ErrorNote error={save.error} />}
    </form>
  );
}

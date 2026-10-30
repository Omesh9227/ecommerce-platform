import { useState } from 'react';
import type { ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';

export function PageHead({ title, children, sub }: { title: string; sub?: ReactNode; children?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-bold sm:text-3xl">{title}</h1>
        {sub && <p className="mt-1 text-sm text-ink-soft">{sub}</p>}
      </div>
      {children && <div className="flex flex-wrap items-center gap-2">{children}</div>}
    </div>
  );
}

export function Tabs<T extends string>({ value, options, onChange, label }: { value: T; options: [T, string][]; onChange: (v: T) => void; label: string }) {
  return (
    <div role="tablist" aria-label={label} className="mb-4 flex flex-wrap gap-2">
      {options.map(([v, text]) => (
        <button
          key={v || 'all'}
          type="button"
          role="tab"
          aria-selected={value === v}
          onClick={() => onChange(v)}
          className={`rounded-full px-3 py-1 text-sm ${value === v ? 'bg-ink text-white' : 'bg-white text-ink hover:bg-mist-deep'}`}
        >
          {text}
        </button>
      ))}
    </div>
  );
}

/** A table that scrolls sideways on narrow screens instead of squeezing its columns. */
export function Table({ head, children, label }: { head: string[]; children: ReactNode; label: string }) {
  return (
    <div className="panel overflow-x-auto">
      <table className="w-full min-w-[40rem] text-left text-sm" aria-label={label}>
        <thead>
          <tr className="border-b border-line text-ink-soft">
            {head.map((h) => (
              <th key={h} scope="col" className="px-4 py-2.5 font-semibold">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-line">{children}</tbody>
      </table>
    </div>
  );
}

export function Pill({ tone = 'plain', children }: { tone?: 'plain' | 'good' | 'warn' | 'bad'; children: ReactNode }) {
  const styles = {
    plain: 'bg-mist-deep text-ink',
    good: 'bg-leaf-pale text-leaf',
    warn: 'bg-marigold-pale text-ink',
    bad: 'bg-chili-pale text-chili',
  }[tone];
  return <span className={`inline-block whitespace-nowrap rounded px-2 py-0.5 text-xs font-semibold ${styles}`}>{children}</span>;
}

/** Filters and paging kept in the address, so a view can be reloaded and shared. */
export function useParam(name: string, fallback = ''): [string, (value: string) => void] {
  const [params, setParams] = useSearchParams();
  const value = params.get(name) ?? fallback;
  // From the latest address, not this render's: two quick changes (search, then a filter) both stick.
  const set = (next: string) =>
    setParams(
      (latest) => {
        const copy = new URLSearchParams(latest);
        if (next && next !== fallback) copy.set(name, next);
        else copy.delete(name);
        if (name !== 'page') copy.delete('page');
        return copy;
      },
      { replace: true },
    );
  return [value, set];
}

export function shortId(id: string | null | undefined) {
  return id ? id.slice(0, 8) : '';
}

export interface FlatCategory {
  id: string;
  name: string;
  depth: number;
  path: string;
  parentId?: string;
  description: string | null;
  childCount: number;
}

/** The category tree as a list in tree order, for selects and tables. */
export function flattenCategories(nodes: { id: string; name: string; description: string | null; children: unknown[] }[], depth = 0, parent?: FlatCategory): FlatCategory[] {
  const out: FlatCategory[] = [];
  for (const n of nodes) {
    const flat: FlatCategory = {
      id: n.id,
      name: n.name,
      depth,
      path: parent ? `${parent.path} / ${n.name}` : n.name,
      parentId: parent?.id,
      description: n.description,
      childCount: n.children.length,
    };
    out.push(flat, ...flattenCategories(n.children as typeof nodes, depth + 1, flat));
  }
  return out;
}

/** A button that asks "are you sure" in place (no browser pop-up): first click arms it, second click acts. */
export function ConfirmButton({ label, confirmLabel, question, onConfirm, busy, className = 'btn-quiet' }: {
  label: string;
  confirmLabel: string;
  question: string;
  onConfirm: () => void;
  busy?: boolean;
  className?: string;
}) {
  const [armed, setArmed] = useState(false);
  if (!armed) {
    return (
      <button type="button" className={className} onClick={() => setArmed(true)}>
        {label}
      </button>
    );
  }
  return (
    <span className="inline-flex flex-wrap items-center gap-2 text-sm">
      <span>{question}</span>
      <button type="button" className="btn-dark px-3 py-1.5" disabled={busy} onClick={onConfirm}>
        {busy ? 'Working…' : confirmLabel}
      </button>
      <button type="button" className="btn-quiet px-3 py-1.5" onClick={() => setArmed(false)}>
        Keep
      </button>
    </span>
  );
}

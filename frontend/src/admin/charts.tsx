import { useState } from 'react';
import type { DailySales, TopProduct } from '../api/admin';
import { date, money } from '../lib/format';

const SALES = '#1A73A8'; // validated with the refund colour: CVD-safe, ≥3:1 on white
const REFUND = '#C2412D';

function niceMax(value: number) {
  if (value <= 0) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const steps = [1, 2, 2.5, 5, 10];
  return (steps.find((s) => s * magnitude >= value) ?? 10) * magnitude;
}

function compact(n: number) {
  const abs = Math.abs(n);
  if (abs >= 1e7) return `${(n / 1e7).toFixed(abs >= 1e8 ? 0 : 1)}Cr`;
  if (abs >= 1e5) return `${(n / 1e5).toFixed(abs >= 1e6 ? 0 : 1)}L`;
  if (abs >= 1e3) return `${(n / 1e3).toFixed(abs >= 1e4 ? 0 : 1)}K`;
  return String(Math.round(n));
}

/** Net sales per day as columns; a day where refunds beat sales dips below the baseline in the refund colour. */
export function SalesChart({ days, currency }: { days: DailySales[]; currency: string }) {
  const [hover, setHover] = useState<number | null>(null);
  const [asTable, setAsTable] = useState(false);
  const W = 720;
  const H = 220;
  const pad = { left: 44, right: 8, top: 12, bottom: 26 };
  const top = niceMax(Math.max(0, ...days.map((d) => d.netSales)));
  const bottom = Math.min(0, ...days.map((d) => d.netSales)) < 0 ? -niceMax(-Math.min(...days.map((d) => d.netSales))) : 0;
  const plotH = H - pad.top - pad.bottom;
  const y = (v: number) => pad.top + ((top - v) / (top - bottom)) * plotH;
  const slot = (W - pad.left - pad.right) / Math.max(days.length, 1);
  const barW = Math.min(24, Math.max(2, slot - 2));
  const ticks = bottom < 0 ? [bottom, 0, top / 2, top] : [0, top / 2, top];
  const labelEvery = Math.ceil(days.length / 8);
  const hovered = hover !== null ? days[hover] : null;

  return (
    <div>
      <div className="mb-2 flex justify-end">
        <button type="button" className="btn-link text-sm" onClick={() => setAsTable((t) => !t)}>
          {asTable ? 'Show chart' : 'Show as table'}
        </button>
      </div>
      {asTable ? (
        <div className="max-h-80 overflow-auto">
          <table className="w-full text-sm" aria-label="Sales per day">
            <thead>
              <tr className="text-left text-ink-soft">
                <th className="py-1 pr-3">Day</th>
                <th className="py-1 pr-3 text-right">Paid orders</th>
                <th className="py-1 pr-3 text-right">Gross</th>
                <th className="py-1 pr-3 text-right">Refunds</th>
                <th className="py-1 text-right">Net</th>
              </tr>
            </thead>
            <tbody>
              {days.map((d) => (
                <tr key={d.date} className="border-t border-line">
                  <td className="py-1 pr-3">{date(d.date)}</td>
                  <td className="py-1 pr-3 text-right">{d.paidOrders}</td>
                  <td className="py-1 pr-3 text-right">{money(d.grossSales, currency)}</td>
                  <td className="py-1 pr-3 text-right">{money(d.refunds, currency)}</td>
                  <td className="py-1 text-right font-semibold">{money(d.netSales, currency)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="relative">
          <svg viewBox={`0 0 ${W} ${H}`} className="h-auto w-full" role="img" aria-label="Net sales per day" onMouseLeave={() => setHover(null)}>
            {ticks.map((t) => (
              <g key={t}>
                <line x1={pad.left} x2={W - pad.right} y1={y(t)} y2={y(t)} stroke={t === 0 ? '#7A8A94' : '#E4E9E4'} strokeWidth={1} />
                <text x={pad.left - 6} y={y(t) + 4} textAnchor="end" fontSize="10" fill="#4A5A66">
                  {compact(t)}
                </text>
              </g>
            ))}
            {days.map((d, i) => {
              const x = pad.left + i * slot + (slot - barW) / 2;
              const v = d.netSales;
              const h = Math.abs(y(v) - y(0));
              const r = Math.min(4, barW / 2, h);
              const color = v < 0 ? REFUND : SALES;
              return (
                <g key={d.date} onMouseEnter={() => setHover(i)} onFocus={() => setHover(i)} tabIndex={0} aria-label={`${date(d.date)}: net ${money(v, currency)}`}>
                  <rect x={pad.left + i * slot} y={pad.top} width={slot} height={plotH} fill="transparent" />
                  {h > 0 &&
                    (v >= 0 ? (
                      <path
                        d={`M${x},${y(0)} V${y(v) + r} Q${x},${y(v)} ${x + r},${y(v)} H${x + barW - r} Q${x + barW},${y(v)} ${x + barW},${y(v) + r} V${y(0)} Z`}
                        fill={color}
                        opacity={hover === null || hover === i ? 1 : 0.55}
                      />
                    ) : (
                      <path
                        d={`M${x},${y(0)} V${y(v) - r} Q${x},${y(v)} ${x + r},${y(v)} H${x + barW - r} Q${x + barW},${y(v)} ${x + barW},${y(v) - r} V${y(0)} Z`}
                        fill={color}
                        opacity={hover === null || hover === i ? 1 : 0.55}
                      />
                    ))}
                  {i % labelEvery === 0 && (
                    <text x={pad.left + i * slot + slot / 2} y={H - 8} textAnchor="middle" fontSize="10" fill="#4A5A66">
                      {new Date(`${d.date}T00:00:00`).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}
                    </text>
                  )}
                </g>
              );
            })}
          </svg>
          {hovered && (
            <div
              role="status"
              className="pointer-events-none absolute top-0 rounded-md border border-line bg-white px-3 py-2 text-xs shadow-lg"
              style={{ left: `${Math.min(80, ((pad.left + hover! * slot) / W) * 100)}%` }}
            >
              <p className="font-semibold">{date(hovered.date)}</p>
              <p>Net {money(hovered.netSales, currency)}</p>
              <p className="text-ink-soft">
                {hovered.paidOrders} paid, gross {money(hovered.grossSales, currency)}
                {hovered.refunds > 0 ? `, refunds ${money(hovered.refunds, currency)}` : ''}
              </p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

/** Best sellers as horizontal bars, longest first, with the value at the tip. */
export function TopBars({ items, by, currency }: { items: TopProduct[]; by: 'revenue' | 'units'; currency: string }) {
  const value = (p: TopProduct) => (by === 'revenue' ? p.revenue : p.unitsSold);
  const max = Math.max(1, ...items.map(value));
  return (
    <ul className="space-y-2.5">
      {items.map((p) => (
        <li key={p.productId} className="text-sm">
          <div className="flex justify-between gap-3">
            <span className="truncate font-medium">{p.name}</span>
            <span className="shrink-0 font-semibold">{by === 'revenue' ? money(p.revenue, currency) : `${p.unitsSold} sold`}</span>
          </div>
          <div className="mt-1 h-2.5 rounded-r bg-transparent">
            <div className="h-full rounded-r" style={{ width: `${(value(p) / max) * 100}%`, backgroundColor: SALES }} />
          </div>
          <p className="mt-0.5 text-xs text-ink-soft">
            {by === 'revenue' ? `${p.unitsSold} ${p.unitsSold === 1 ? 'unit' : 'units'}` : money(p.revenue, currency)} in {p.orders} {p.orders === 1 ? 'order' : 'orders'}
          </p>
        </li>
      ))}
    </ul>
  );
}

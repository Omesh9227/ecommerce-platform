const moneyFormats = new Map<string, Intl.NumberFormat>();

/** ₹1,23,456.00 (Indian digit grouping). */
export function money(amount: number | null | undefined, currency = 'INR'): string {
  if (amount === null || amount === undefined || Number.isNaN(amount)) return '—';
  let format = moneyFormats.get(currency);
  if (!format) {
    format = new Intl.NumberFormat('en-IN', { style: 'currency', currency, minimumFractionDigits: 2 });
    moneyFormats.set(currency, format);
  }
  return format.format(amount);
}

/** Splits a price for display: { symbol: '₹', whole: '1,499', fraction: '00' }. */
export function priceParts(amount: number, currency = 'INR') {
  const parts = new Intl.NumberFormat('en-IN', { style: 'currency', currency, minimumFractionDigits: 2 }).formatToParts(amount);
  const symbol = parts.find((p) => p.type === 'currency')?.value ?? '';
  const fraction = parts.find((p) => p.type === 'fraction')?.value ?? '00';
  const whole = parts
    .filter((p) => p.type === 'integer' || p.type === 'group')
    .map((p) => p.value)
    .join('');
  return { symbol, whole: (amount < 0 ? '-' : '') + whole, fraction };
}

export function dateTime(iso: string | null | undefined): string {
  if (!iso) return '';
  return new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' });
}

export function date(iso: string | null | undefined): string {
  if (!iso) return '';
  // LocalDate strings (2026-10-29) are calendar days: show them without shifting time zones
  const value = /^\d{4}-\d{2}-\d{2}$/.test(iso) ? new Date(`${iso}T00:00:00`) : new Date(iso);
  return value.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' });
}

export function plural(count: number, one: string, many = `${one}s`): string {
  return `${count} ${count === 1 ? one : many}`;
}

export function percentOff(discountPercent: number | null | undefined): string | null {
  if (!discountPercent || discountPercent <= 0) return null;
  return `${Math.round(discountPercent)}% off`;
}

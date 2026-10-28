import { money, percentOff, priceParts } from '../lib/format';

interface Props {
  amount: number;
  currency?: string;
  listPrice?: number | null;
  discountPercent?: number | null;
  size?: 'sm' | 'md' | 'lg';
}

/** The price is the loudest thing on a product: big rupees, small paise, the struck list price beside it. */
export function Price({ amount, currency = 'INR', listPrice, discountPercent, size = 'md' }: Props) {
  const { symbol, whole, fraction } = priceParts(amount, currency);
  const wholeSize = size === 'lg' ? 'text-4xl' : size === 'md' ? 'text-2xl' : 'text-lg';
  const smallSize = size === 'lg' ? 'text-base' : 'text-xs';
  const off = percentOff(discountPercent);
  const showList = listPrice !== null && listPrice !== undefined && listPrice > amount;
  return (
    <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
      <span className="font-display font-extrabold leading-none text-ink" aria-label={money(amount, currency)}>
        <span aria-hidden className={`${smallSize} align-top`}>
          {symbol}
        </span>
        <span aria-hidden className={wholeSize}>
          {whole}
        </span>
        <span aria-hidden className={`${smallSize} align-top`}>
          {fraction}
        </span>
      </span>
      {showList && (
        <span className="text-sm text-ink-faint line-through" aria-label={`List price ${money(listPrice, currency)}`}>
          {money(listPrice, currency)}
        </span>
      )}
      {off && <span className="text-sm font-semibold text-leaf">{off}</span>}
    </div>
  );
}

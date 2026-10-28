interface Props {
  rating: number | null | undefined;
  count?: number;
  size?: 'sm' | 'md';
}

/** Five stars, filled to the average (to the nearest half), with the review count. */
export function Stars({ rating, count, size = 'sm' }: Props) {
  const value = rating ?? 0;
  const rounded = Math.round(value * 2) / 2;
  const label = count === 0 || !rating ? 'No reviews yet' : `${value.toFixed(1)} out of 5${count !== undefined ? `, ${count} reviews` : ''}`;
  const px = size === 'md' ? 18 : 14;
  return (
    <span className="inline-flex items-center gap-1.5" aria-label={label} role="img">
      <span className="inline-flex" aria-hidden>
        {[1, 2, 3, 4, 5].map((i) => (
          <Star key={i} fill={rounded >= i ? 1 : rounded >= i - 0.5 ? 0.5 : 0} px={px} />
        ))}
      </span>
      {count !== undefined && (
        <span aria-hidden className="text-xs text-ink-soft">
          {count === 0 ? 'No reviews' : count.toLocaleString('en-IN')}
        </span>
      )}
    </span>
  );
}

function Star({ fill, px }: { fill: number; px: number }) {
  const id = `half-${px}`;
  return (
    <svg width={px} height={px} viewBox="0 0 20 20">
      {fill === 0.5 && (
        <defs>
          <linearGradient id={id}>
            <stop offset="50%" stopColor="#F2A900" />
            <stop offset="50%" stopColor="#D6DDD7" />
          </linearGradient>
        </defs>
      )}
      <path
        d="M10 1.5l2.6 5.5 6 .7-4.4 4.1 1.2 6-5.4-3-5.4 3 1.2-6L1.4 7.7l6-.7z"
        fill={fill === 1 ? '#F2A900' : fill === 0.5 ? `url(#${id})` : '#D6DDD7'}
      />
    </svg>
  );
}

import { useState } from 'react';

const SHADES = ['#E4E9E4', '#FFF4D6', '#E3F2EA', '#E6ECF0', '#F6E7DF'];

/** The product's photo, or, when there is none (or it doesn't load), a tile with its initials. */
export function ProductImage({ src, name, className = '' }: { src?: string | null; name: string; className?: string }) {
  const [failed, setFailed] = useState(false);
  if (src && !failed) {
    return (
      <img
        src={src}
        alt={name}
        loading="lazy"
        onError={() => setFailed(true)}
        className={`h-full w-full object-contain ${className}`}
      />
    );
  }
  const initials = name
    .split(/\s+/)
    .filter((w) => /[A-Za-z0-9]/.test(w[0] ?? ''))
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join('');
  const shade = SHADES[[...name].reduce((sum, c) => sum + c.charCodeAt(0), 0) % SHADES.length];
  return (
    <div
      role="img"
      aria-label={name}
      className={`flex h-full w-full items-center justify-center font-display text-3xl font-extrabold text-ink/70 ${className}`}
      style={{ backgroundColor: shade }}
    >
      {initials || '?'}
    </div>
  );
}

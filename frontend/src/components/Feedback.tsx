import type { ReactNode } from 'react';
import { ApiError } from '../api/client';

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <div role="status" className="flex items-center gap-3 py-10 text-ink-soft">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-line border-t-ink" aria-hidden />
      {label}
    </div>
  );
}

export function ErrorNote({ error, retry }: { error: unknown; retry?: () => void }) {
  const message = error instanceof ApiError || error instanceof Error ? error.message : 'Something went wrong.';
  return (
    <div role="alert" className="rounded-md border border-chili/40 bg-chili-pale px-4 py-3 text-sm text-chili">
      {message}
      {retry && (
        <button type="button" onClick={retry} className="ml-3 font-semibold underline">
          Try again
        </button>
      )}
    </div>
  );
}

export function Notice({ tone = 'info', children }: { tone?: 'info' | 'good' | 'warn'; children: ReactNode }) {
  const styles = {
    info: 'border-line bg-white text-ink',
    good: 'border-leaf/40 bg-leaf-pale text-leaf',
    warn: 'border-marigold-deep/50 bg-marigold-pale text-ink',
  }[tone];
  return (
    <div role="status" className={`rounded-md border px-4 py-3 text-sm ${styles}`}>
      {children}
    </div>
  );
}

export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="py-14 text-center">
      <h2 className="text-2xl font-bold">{title}</h2>
      {children && <div className="mx-auto mt-3 max-w-md text-ink-soft">{children}</div>}
    </div>
  );
}

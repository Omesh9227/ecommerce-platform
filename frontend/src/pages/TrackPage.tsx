import { useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { isApiError } from '../api/client';
import { shipping } from '../api/shop';
import { ErrorNote, Loading } from '../components/Feedback';
import { shipmentStatusLabel } from '../components/StatusBadge';
import { date } from '../lib/format';
import { TrackingList } from './OrderPage';

/** Anyone with a tracking number can follow the parcel (no sign-in, no personal details shown). */
export function TrackPage() {
  const { trackingNumber = '' } = useParams();
  const navigate = useNavigate();
  const [text, setText] = useState(trackingNumber);
  const tracking = useQuery({
    queryKey: ['track', trackingNumber],
    queryFn: () => shipping.track(trackingNumber),
    enabled: !!trackingNumber,
    retry: (n, e) => !isApiError(e, 404) && n < 2,
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const value = text.trim().toUpperCase();
    if (value) navigate(`/track/${encodeURIComponent(value)}`);
  }

  const t = tracking.data;
  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="text-3xl font-bold">Track a parcel</h1>
      <form onSubmit={onSubmit} className="mt-4 flex gap-2">
        <label htmlFor="tracking" className="sr-only">
          Tracking number
        </label>
        <input id="tracking" className="field" placeholder="EC123456789012" value={text} onChange={(e) => setText(e.target.value)} />
        <button type="submit" className="btn-dark">
          Track
        </button>
      </form>
      {tracking.isLoading && trackingNumber && <Loading />}
      {isApiError(tracking.error, 404) && (
        <p className="mt-6 text-ink-soft">No parcel has the tracking number {trackingNumber}. Check it against your shipping email.</p>
      )}
      {tracking.isError && !isApiError(tracking.error, 404) && <ErrorNote error={tracking.error} retry={() => tracking.refetch()} />}
      {t && (
        <section className="panel mt-6 p-5" data-testid="tracking">
          <p className="text-sm text-ink-soft">
            {t.carrier}, {t.trackingNumber}
          </p>
          <h2 className="mt-1 text-2xl font-bold">{shipmentStatusLabel(t.status)}</h2>
          <p className="mt-1 text-sm">
            To {t.destination}
            {t.estimatedDeliveryDate && (
              <>
                , arriving by <span className="font-semibold">{date(t.estimatedDeliveryDate)}</span>
              </>
            )}
          </p>
          <TrackingList events={[...t.events].reverse()} />
        </section>
      )}
    </div>
  );
}

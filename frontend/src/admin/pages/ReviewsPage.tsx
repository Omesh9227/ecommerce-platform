import { useState, type FormEvent } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { adminReviews, type AdminReview } from '../../api/admin';
import { ErrorNote, Loading } from '../../components/Feedback';
import { Pager } from '../../components/Pager';
import { Stars } from '../../components/Stars';
import { dateTime } from '../../lib/format';
import { ConfirmButton, PageHead, Pill, Tabs, shortId, useParam } from '../ui';

export function ReviewsPage() {
  const [status, setStatus] = useParam('status');
  const [productId, setProductId] = useParam('product');
  const [page, setPage] = useParam('page', '0');
  const list = useQuery({
    queryKey: ['admin-reviews', status, productId, page],
    queryFn: () => adminReviews.list(Number(page), (status || undefined) as 'VISIBLE' | 'HIDDEN' | undefined, productId || undefined),
    placeholderData: keepPreviousData,
    meta: { private: true },
  });
  return (
    <div className="max-w-4xl">
      <PageHead title="Reviews" sub="Hidden reviews disappear from the product page and its rating; the author still sees them with your reason." />
      {productId && (
        <p className="mb-3 text-sm">
          Reviews of one product{' '}
          <button type="button" className="btn-link" onClick={() => setProductId('')}>
            Show all
          </button>
        </p>
      )}
      <Tabs label="Status" value={status} options={[['', 'All'], ['VISIBLE', 'Visible'], ['HIDDEN', 'Hidden']]} onChange={setStatus} />
      {list.isLoading && <Loading />}
      {list.isError && <ErrorNote error={list.error} retry={() => list.refetch()} />}
      <ul className="space-y-3">
        {list.data?.content.map((r) => (
          <ReviewCard key={r.id} review={r} />
        ))}
      </ul>
      {list.data && list.data.content.length === 0 && <p className="text-ink-soft">No reviews here.</p>}
      {list.data && <Pager page={list.data.page} totalPages={list.data.totalPages} onPage={(p) => setPage(String(p))} />}
    </div>
  );
}

function ReviewCard({ review }: { review: AdminReview }) {
  const queryClient = useQueryClient();
  const [hiding, setHiding] = useState(false);
  const [reason, setReason] = useState('');
  const done = () => {
    queryClient.invalidateQueries({ queryKey: ['admin-reviews'] });
    setHiding(false);
    setReason('');
  };
  const hide = useMutation({ mutationFn: () => adminReviews.hide(review.id, reason.trim()), onSuccess: done });
  const unhide = useMutation({ mutationFn: () => adminReviews.unhide(review.id), onSuccess: done });
  const remove = useMutation({ mutationFn: () => adminReviews.remove(review.id), onSuccess: done });
  const error = hide.error ?? unhide.error ?? remove.error;

  function onHide(e: FormEvent) {
    e.preventDefault();
    if (reason.trim()) hide.mutate();
  }

  return (
    <li className="panel p-4 text-sm" data-testid="admin-review">
      <div className="flex flex-wrap items-center gap-3">
        <Stars rating={review.rating} />
        <span className="font-display font-bold">{review.title}</span>
        {review.status === 'HIDDEN' ? <Pill tone="bad">Hidden</Pill> : <Pill tone="good">Visible</Pill>}
        {review.verifiedPurchase && <Pill>Verified purchase</Pill>}
      </div>
      {review.comment && <p className="mt-2 max-w-prose whitespace-pre-line">{review.comment}</p>}
      {review.hiddenReason && <p className="mt-2 text-chili">Hidden because: {review.hiddenReason}</p>}
      <p className="mt-2 text-xs text-ink-soft">
        {review.reviewerName}
        {review.userId ? ` (${shortId(review.userId)})` : ''}, {dateTime(review.createdAt)}, {review.helpfulCount} found it helpful,{' '}
        <Link to={`/admin/products/${review.productId}`} className="underline">
          product
        </Link>
      </p>
      <div className="mt-3 flex flex-wrap items-center gap-4">
        {review.status === 'VISIBLE' && !hiding && (
          <button type="button" className="btn-quiet px-3 py-1.5" onClick={() => setHiding(true)}>
            Hide
          </button>
        )}
        {review.status === 'HIDDEN' && (
          <button type="button" className="btn-quiet px-3 py-1.5" disabled={unhide.isPending} onClick={() => unhide.mutate()}>
            Show again
          </button>
        )}
        <ConfirmButton label="Delete" className="btn-link" question="Delete this review for good?" confirmLabel="Delete" busy={remove.isPending} onConfirm={() => remove.mutate()} />
      </div>
      {hiding && (
        <form onSubmit={onHide} className="mt-3 flex flex-wrap gap-2" aria-label="Hide review">
          <input aria-label="Reason for hiding" className="field max-w-md" maxLength={300} placeholder="Reason, shown to the author" value={reason} onChange={(e) => setReason(e.target.value)} />
          <button type="submit" className="btn-dark px-3 py-1.5" disabled={!reason.trim() || hide.isPending}>
            Hide review
          </button>
          <button type="button" className="btn-quiet px-3 py-1.5" onClick={() => setHiding(false)}>
            Cancel
          </button>
        </form>
      )}
      {error && (
        <div className="mt-2">
          <ErrorNote error={error} />
        </div>
      )}
    </li>
  );
}

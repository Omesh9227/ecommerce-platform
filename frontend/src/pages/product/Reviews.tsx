import { useState, type FormEvent } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useLocation } from 'react-router-dom';
import { isApiError } from '../../api/client';
import { reviews } from '../../api/shop';
import type { Review, ReviewInput } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { ErrorNote } from '../../components/Feedback';
import { Pager } from '../../components/Pager';
import { Stars } from '../../components/Stars';
import { dateTime } from '../../lib/format';

const SORTS: [string, string][] = [
  ['createdAt,desc', 'Newest'],
  ['helpfulCount,desc', 'Most helpful'],
  ['rating,desc', 'Highest rating'],
  ['rating,asc', 'Lowest rating'],
];

export function Reviews({ productId }: { productId: string }) {
  const { user } = useAuth();
  const location = useLocation();
  const [rating, setRating] = useState<number | undefined>();
  const [verifiedOnly, setVerifiedOnly] = useState(false);
  const [sort, setSort] = useState(SORTS[0][0]);
  const [page, setPage] = useState(0);
  const [writing, setWriting] = useState(false);

  const summary = useQuery({ queryKey: ['rating-summary', productId], queryFn: () => reviews.summary(productId) });
  const list = useQuery({
    queryKey: ['reviews', productId, rating, verifiedOnly, sort, page],
    queryFn: () => reviews.forProduct(productId, { rating, verifiedOnly: verifiedOnly || undefined, sort, page }),
    placeholderData: keepPreviousData,
  });

  const s = summary.data;
  return (
    <section id="reviews" className="mt-12 grid gap-8 border-t border-line pt-8 md:grid-cols-[18rem_1fr]">
      <div>
        <h2 className="text-2xl font-bold">Customer reviews</h2>
        {s && (
          <>
            <div className="mt-2 flex items-center gap-3">
              <Stars rating={s.averageRating} size="md" />
              <span className="font-display text-lg font-bold" data-testid="average-rating">
                {s.reviewCount > 0 ? `${s.averageRating.toFixed(1)} out of 5` : 'No reviews yet'}
              </span>
            </div>
            <p className="text-sm text-ink-soft">{s.reviewCount.toLocaleString('en-IN')} ratings</p>
            <ul className="mt-4 space-y-1.5">
              {[5, 4, 3, 2, 1].map((stars) => {
                const n = s.ratingCounts[String(stars)] ?? 0;
                const share = s.reviewCount ? Math.round((n * 100) / s.reviewCount) : 0;
                return (
                  <li key={stars}>
                    <button
                      type="button"
                      className={`flex w-full items-center gap-2 text-sm hover:underline ${rating === stars ? 'font-bold' : ''}`}
                      aria-pressed={rating === stars}
                      onClick={() => {
                        setRating(rating === stars ? undefined : stars);
                        setPage(0);
                      }}
                    >
                      <span className="w-12 text-left">{stars} star</span>
                      <span className="h-3 flex-1 overflow-hidden rounded-sm bg-mist-deep">
                        <span className="block h-full bg-marigold" style={{ width: `${share}%` }} />
                      </span>
                      <span className="w-10 text-right text-ink-soft">{share}%</span>
                    </button>
                  </li>
                );
              })}
            </ul>
          </>
        )}
        <div className="mt-6 border-t border-line pt-4">
          <h3 className="font-bold">Used this product?</h3>
          {user ? (
            <button type="button" className="btn-quiet mt-2 w-full" onClick={() => setWriting(true)}>
              Write a review
            </button>
          ) : (
            <Link to="/login" state={{ from: location.pathname }} className="btn-quiet mt-2 w-full">
              Sign in to write a review
            </Link>
          )}
        </div>
      </div>

      <div>
        {writing && <ReviewForm productId={productId} onDone={() => setWriting(false)} />}
        <div className="mb-4 flex flex-wrap items-center gap-4 text-sm">
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              className="h-4 w-4 accent-ink"
              checked={verifiedOnly}
              onChange={(e) => {
                setVerifiedOnly(e.target.checked);
                setPage(0);
              }}
            />
            Verified purchases only
          </label>
          <label className="flex items-center gap-2">
            Sort
            <select className="field w-auto py-1.5" value={sort} onChange={(e) => setSort(e.target.value)}>
              {SORTS.map(([v, l]) => (
                <option key={v} value={v}>
                  {l}
                </option>
              ))}
            </select>
          </label>
          {rating && (
            <button type="button" className="btn-link" onClick={() => setRating(undefined)}>
              Show all ratings
            </button>
          )}
        </div>
        {list.isError && <ErrorNote error={list.error} retry={() => list.refetch()} />}
        {list.data && list.data.content.length === 0 && (
          <p className="text-ink-soft">{rating || verifiedOnly ? 'No reviews match.' : 'Be the first to review this product.'}</p>
        )}
        <ul className="divide-y divide-line">
          {list.data?.content.map((r) => (
            <ReviewItem key={r.id} review={r} />
          ))}
        </ul>
        {list.data && <Pager page={list.data.page} totalPages={list.data.totalPages} onPage={setPage} />}
      </div>
    </section>
  );
}

function ReviewItem({ review }: { review: Review }) {
  const { user } = useAuth();
  const [vote, setVote] = useState<{ count: number; mine: boolean }>({ count: review.helpfulCount, mine: false });
  const helpful = useMutation({
    mutationFn: () => (vote.mine ? reviews.notHelpful(review.id) : reviews.helpful(review.id)),
    onSuccess: (r) => setVote({ count: r.helpfulCount, mine: r.votedByMe }),
  });
  return (
    <li className="py-5" data-testid="review">
      <div className="flex flex-wrap items-center gap-3">
        <span className="font-semibold">{review.reviewerName}</span>
        {review.verifiedPurchase && <span className="text-xs font-semibold text-leaf">Verified purchase</span>}
      </div>
      <div className="mt-1 flex items-center gap-3">
        <Stars rating={review.rating} />
        <span className="font-display font-bold">{review.title}</span>
      </div>
      <p className="mt-1 text-xs text-ink-soft">
        {dateTime(review.createdAt)}
        {review.editedAt ? ' (edited)' : ''}
      </p>
      {review.comment && <p className="mt-2 max-w-prose whitespace-pre-line leading-relaxed">{review.comment}</p>}
      <div className="mt-3 flex items-center gap-3 text-sm text-ink-soft">
        {vote.count > 0 && <span>{vote.count === 1 ? '1 person found this helpful' : `${vote.count} people found this helpful`}</span>}
        {user && (
          <button type="button" className="btn-quiet px-3 py-1" aria-pressed={vote.mine} disabled={helpful.isPending} onClick={() => helpful.mutate()}>
            {vote.mine ? 'Marked helpful' : 'Helpful'}
          </button>
        )}
        {helpful.isError && <span className="text-chili">{(helpful.error as Error).message}</span>}
      </div>
    </li>
  );
}

export function ReviewForm({
  productId,
  existing,
  onDone,
}: {
  productId: string;
  existing?: Review;
  onDone: () => void;
}) {
  const queryClient = useQueryClient();
  const [rating, setRating] = useState(existing?.rating ?? 0);
  const [title, setTitle] = useState(existing?.title ?? '');
  const [comment, setComment] = useState(existing?.comment ?? '');
  const [problem, setProblem] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: (input: ReviewInput) => (existing ? reviews.edit(existing.id, input) : reviews.create(productId, input)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reviews', productId] });
      queryClient.invalidateQueries({ queryKey: ['rating-summary', productId] });
      queryClient.invalidateQueries({ queryKey: ['my-reviews'] });
      onDone();
    },
  });

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (rating < 1) return setProblem('Choose a star rating.');
    if (!title.trim()) return setProblem('Give your review a title.');
    setProblem(null);
    save.mutate({ rating, title: title.trim(), comment: comment.trim() || undefined });
  }

  const alreadyReviewed = isApiError(save.error, 409);
  return (
    <form onSubmit={onSubmit} className="panel mb-6 space-y-4 p-5" aria-label={existing ? 'Edit your review' : 'Write a review'}>
      <fieldset>
        <legend className="mb-1 text-sm font-semibold">Your rating</legend>
        <div className="flex gap-1">
          {[1, 2, 3, 4, 5].map((n) => (
            <button
              key={n}
              type="button"
              aria-label={`${n} star${n > 1 ? 's' : ''}`}
              aria-pressed={rating === n}
              onClick={() => setRating(n)}
              className={`h-9 w-9 rounded font-display text-lg font-bold ${n <= rating ? 'bg-marigold text-ink' : 'bg-mist-deep text-ink-soft'}`}
            >
              {n}
            </button>
          ))}
        </div>
      </fieldset>
      <div>
        <label htmlFor="review-title" className="mb-1 block text-sm font-semibold">
          Title
        </label>
        <input id="review-title" className="field" maxLength={150} value={title} onChange={(e) => setTitle(e.target.value)} />
      </div>
      <div>
        <label htmlFor="review-comment" className="mb-1 block text-sm font-semibold">
          Your review <span className="font-normal text-ink-soft">(optional)</span>
        </label>
        <textarea id="review-comment" className="field min-h-28" maxLength={4000} value={comment} onChange={(e) => setComment(e.target.value)} />
      </div>
      {problem && <p className="text-sm text-chili">{problem}</p>}
      {save.isError &&
        (alreadyReviewed ? (
          <ErrorNote error={new Error('You’ve already reviewed this product. You can change it under Your account → Your reviews.')} />
        ) : (
          <ErrorNote error={save.error} />
        ))}
      <div className="flex gap-3">
        <button type="submit" className="btn-dark" disabled={save.isPending}>
          {save.isPending ? 'Saving…' : existing ? 'Save changes' : 'Post review'}
        </button>
        <button type="button" className="btn-quiet" onClick={onDone}>
          Cancel
        </button>
      </div>
    </form>
  );
}

import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ApiError } from '../api/client';
import { auth, recommendations, reviews } from '../api/shop';
import type { Review } from '../api/types';
import { ErrorNote, Loading, Notice } from '../components/Feedback';
import { Field } from '../components/Field';
import { ProductRow } from '../components/ProductRow';
import { Stars } from '../components/Stars';
import { dateTime } from '../lib/format';
import { rules } from '../lib/validation';
import { ReviewForm } from './product/Reviews';

export function AccountPage() {
  return (
    <div className="space-y-10">
      <h1 className="text-3xl font-bold">Your account</h1>
      <Profile />
      <MyReviews />
      <RecentlyViewed />
    </div>
  );
}

function Profile() {
  const queryClient = useQueryClient();
  const me = useQuery({ queryKey: ['me'], queryFn: auth.me, meta: { private: true } });
  const [form, setForm] = useState({ firstName: '', lastName: '', phoneNumber: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  useEffect(() => {
    if (me.data) setForm({ firstName: me.data.firstName, lastName: me.data.lastName, phoneNumber: me.data.phoneNumber });
  }, [me.data]);
  const save = useMutation({
    mutationFn: () => auth.updateMe({ firstName: form.firstName.trim(), lastName: form.lastName.trim(), phoneNumber: form.phoneNumber }),
    onSuccess: (profile) => queryClient.setQueryData(['me'], profile),
    onError: (e) => e instanceof ApiError && setErrors(e.fieldErrors),
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const found: Record<string, string> = {};
    if (!rules.name.test(form.firstName.trim())) found.firstName = 'Enter your first name';
    if (!rules.name.test(form.lastName.trim())) found.lastName = 'Enter your last name';
    if (!rules.phone.test(form.phoneNumber)) found.phoneNumber = 'Enter 10 to 15 digits';
    setErrors(found);
    if (Object.keys(found).length === 0) save.mutate();
  }

  if (me.isLoading) return <Loading />;
  if (me.isError) return <ErrorNote error={me.error} retry={() => me.refetch()} />;
  return (
    <section className="panel max-w-2xl p-5">
      <h2 className="text-xl font-bold">Profile</h2>
      <p className="mt-1 text-sm text-ink-soft">
        {me.data!.email}, member since {dateTime(me.data!.createdAt)}
      </p>
      <form onSubmit={onSubmit} noValidate className="mt-4 grid gap-4 sm:grid-cols-2">
        <Field label="First name" value={form.firstName} error={errors.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })} />
        <Field label="Last name" value={form.lastName} error={errors.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })} />
        <Field
          label="Mobile number"
          inputMode="numeric"
          value={form.phoneNumber}
          error={errors.phoneNumber}
          onChange={(e) => setForm({ ...form, phoneNumber: e.target.value.replace(/\D/g, '') })}
        />
        <div className="flex items-end gap-3 sm:col-span-2">
          <button type="submit" className="btn-dark" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save profile'}
          </button>
          {save.isSuccess && <span className="text-sm text-leaf">Saved</span>}
        </div>
        {save.isError && Object.keys(errors).length === 0 && (
          <div className="sm:col-span-2">
            <ErrorNote error={save.error} />
          </div>
        )}
      </form>
    </section>
  );
}

function MyReviews() {
  const queryClient = useQueryClient();
  const mine = useQuery({ queryKey: ['my-reviews'], queryFn: () => reviews.mine(0), meta: { private: true } });
  const [editing, setEditing] = useState<string | null>(null);
  const remove = useMutation({
    mutationFn: (r: Review) => reviews.remove(r.id),
    onSuccess: (_, r) => {
      queryClient.invalidateQueries({ queryKey: ['my-reviews'] });
      queryClient.invalidateQueries({ queryKey: ['reviews', r.productId] });
      queryClient.invalidateQueries({ queryKey: ['rating-summary', r.productId] });
    },
  });
  return (
    <section>
      <h2 className="text-xl font-bold">Your reviews</h2>
      {mine.isLoading && <Loading />}
      {mine.isError && <ErrorNote error={mine.error} />}
      {mine.data?.content.length === 0 && <p className="mt-2 text-ink-soft">You haven’t reviewed anything yet.</p>}
      <ul className="mt-3 space-y-3">
        {mine.data?.content.map((r) =>
          editing === r.id ? (
            <li key={r.id}>
              <ReviewForm productId={r.productId} existing={r} onDone={() => setEditing(null)} />
            </li>
          ) : (
            <li key={r.id} className="panel p-4" data-testid="my-review">
              <div className="flex flex-wrap items-center gap-3">
                <Stars rating={r.rating} />
                <Link to={`/products/${r.productId}`} className="font-display font-bold hover:underline">
                  {r.title}
                </Link>
                {r.verifiedPurchase && <span className="text-xs font-semibold text-leaf">Verified purchase</span>}
              </div>
              {r.status === 'HIDDEN' && (
                <div className="mt-2">
                  <Notice tone="warn">Hidden by the shop{r.hiddenReason ? `: ${r.hiddenReason}` : ''}</Notice>
                </div>
              )}
              {r.comment && <p className="mt-2 text-sm">{r.comment}</p>}
              <div className="mt-3 flex gap-4 text-sm">
                <button type="button" className="btn-link" onClick={() => setEditing(r.id)}>
                  Edit
                </button>
                <button type="button" className="btn-link" disabled={remove.isPending} onClick={() => remove.mutate(r)}>
                  Delete
                </button>
              </div>
            </li>
          ),
        )}
      </ul>
    </section>
  );
}

function RecentlyViewed() {
  const queryClient = useQueryClient();
  const recent = useQuery({ queryKey: ['recently-viewed'], queryFn: () => recommendations.recentlyViewed(12), meta: { private: true } });
  const clear = useMutation({
    mutationFn: recommendations.clearRecentlyViewed,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['recently-viewed'] }),
  });
  if (!recent.data || recent.data.products.length === 0) return null;
  return (
    <div>
      <ProductRow list={recent.data} title="Recently viewed" />
      <button type="button" className="btn-link mt-2 text-sm" disabled={clear.isPending} onClick={() => clear.mutate()}>
        Clear your viewing history
      </button>
    </div>
  );
}

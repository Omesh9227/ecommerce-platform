import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { catalog, recommendations, search } from '../api/shop';
import { useAuth } from '../auth/AuthContext';
import { ProductRow } from '../components/ProductRow';

export function HomePage() {
  const { user } = useAuth();
  const popular = useQuery({ queryKey: ['popular'], queryFn: () => search.popular(8) });
  const categories = useQuery({ queryKey: ['categories'], queryFn: catalog.categories, staleTime: 5 * 60_000 });
  const trending = useQuery({ queryKey: ['trending'], queryFn: () => recommendations.trending(6) });
  const forYou = useQuery({ queryKey: ['for-you'], queryFn: () => recommendations.forYou(6), enabled: !!user, meta: { private: true } });
  const recent = useQuery({
    queryKey: ['recently-viewed'],
    queryFn: () => recommendations.recentlyViewed(6),
    enabled: !!user,
    meta: { private: true },
  });

  return (
    <div>
      <section className="rounded-xl bg-ink px-6 py-8 text-white sm:px-10 sm:py-12">
        <h1 className="max-w-2xl font-display text-4xl font-extrabold leading-[1.05] text-white sm:text-6xl">
          {user ? 'Welcome back. What’s on the list today?' : 'Everything for the house, the desk and the road.'}
        </h1>
        {popular.data && popular.data.length > 0 && (
          <div className="mt-6">
            <h2 className="mb-2 font-sans text-sm font-semibold text-white/80">People are searching for</h2>
            <ul className="flex flex-wrap gap-2">
              {popular.data.map((p) => (
                <li key={p.term}>
                  <Link
                    to={`/search?q=${encodeURIComponent(p.term)}`}
                    className="inline-block rounded-full border border-white/30 px-3 py-1 text-sm hover:border-marigold hover:text-marigold"
                  >
                    {p.term}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        )}
        {!user && (
          <p className="mt-6 text-sm text-white/80">
            <Link to="/login" className="font-semibold text-marigold underline underline-offset-4">
              Sign in
            </Link>{' '}
            for recommendations picked from what you buy and view.
          </p>
        )}
      </section>

      <ProductRow list={forYou.data} testId="for-you" />
      <ProductRow list={trending.data} testId="trending" />
      <ProductRow list={recent.data} title="Recently viewed" testId="recently-viewed" />

      {categories.data && categories.data.length > 0 && (
        <section className="mt-10">
          <h2 className="mb-3 text-xl font-bold">Shop by category</h2>
          <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
            {categories.data.map((c) => (
              <li key={c.id}>
                <Link to={`/search?categoryId=${c.id}`} className="panel block h-full px-4 py-4 hover:border-ink">
                  <span className="font-display text-lg font-bold">{c.name}</span>
                  {c.children.length > 0 && (
                    <span className="mt-1 block text-sm text-ink-soft">{c.children.map((s) => s.name).join(', ')}</span>
                  )}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}

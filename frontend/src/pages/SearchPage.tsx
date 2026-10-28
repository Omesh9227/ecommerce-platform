import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { search, type SearchParams } from '../api/shop';
import type { Facets } from '../api/types';
import { Empty, ErrorNote, Loading } from '../components/Feedback';
import { Pager } from '../components/Pager';
import { ProductTile } from '../components/ProductTile';
import { plural } from '../lib/format';

const SORTS: [string, string][] = [
  ['relevance', 'Best match'],
  ['price_asc', 'Price: low to high'],
  ['price_desc', 'Price: high to low'],
  ['rating', 'Customer rating'],
  ['newest', 'Newest'],
];

/** Results and filters both live in the address, so a search can be shared, reloaded and navigated back to. */
export function SearchPage() {
  const [params, setParams] = useSearchParams();
  const [filtersOpen, setFiltersOpen] = useState(false);

  const request: SearchParams = {
    q: params.get('q') ?? undefined,
    categoryId: params.get('categoryId') ?? undefined,
    brand: params.getAll('brand'),
    minPrice: params.get('minPrice') ?? undefined,
    maxPrice: params.get('maxPrice') ?? undefined,
    minRating: params.get('minRating') ?? undefined,
    inStock: params.get('inStock') ?? undefined,
    sort: params.get('sort') ?? undefined,
    page: Number(params.get('page') ?? 0),
    size: 24,
  };

  const result = useQuery({
    queryKey: ['search', params.toString()],
    queryFn: ({ signal }) => search.products(request, signal),
    placeholderData: keepPreviousData,
  });

  function update(changes: Record<string, string | string[] | null>) {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      next.delete(key);
      if (Array.isArray(value)) value.forEach((v) => next.append(key, v));
      else if (value !== null && value !== '') next.set(key, value);
    }
    if (!('page' in changes)) next.delete('page');
    setParams(next);
  }

  const data = result.data;
  const query = params.get('q');
  const categoryName = data?.facets?.categories.find((c) => c.id === request.categoryId)?.name;
  const heading = query ? `Results for “${data?.correctedQuery ?? query}”` : categoryName ?? 'All products';
  const activeFilters =
    (request.brand?.length ?? 0) + [request.minPrice, request.maxPrice, request.minRating, request.inStock].filter(Boolean).length;

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold sm:text-3xl">{heading}</h1>
          {data && (
            <p className="text-sm text-ink-soft" data-testid="result-count">
              {plural(data.results.totalElements, 'product')}
            </p>
          )}
        </div>
        <div className="flex items-center gap-3">
          <button type="button" className="btn-quiet md:hidden" onClick={() => setFiltersOpen((o) => !o)} aria-expanded={filtersOpen}>
            Filters{activeFilters > 0 ? ` (${activeFilters})` : ''}
          </button>
          <label className="flex items-center gap-2 text-sm">
            <span className="text-ink-soft">Sort by</span>
            <select
              className="field w-auto py-2"
              value={request.sort ?? 'relevance'}
              onChange={(e) => update({ sort: e.target.value === 'relevance' ? null : e.target.value })}
            >
              {SORTS.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>

      {data?.correctedQuery && query && (
        <p className="mb-4 rounded-md bg-marigold-pale px-4 py-2 text-sm" data-testid="corrected">
          Showing results for <strong>{data.correctedQuery}</strong> instead of “{query}”.
        </p>
      )}

      <div className="grid gap-6 md:grid-cols-[15rem_1fr]">
        <aside className={`${filtersOpen ? 'block' : 'hidden'} md:block`} aria-label="Filters">
          {data?.facets && <FacetPanel facets={data.facets} request={request} update={update} />}
        </aside>
        <div>
          {result.isLoading && <Loading label="Searching…" />}
          {result.isError && <ErrorNote error={result.error} retry={() => result.refetch()} />}
          {data && data.results.content.length === 0 && (
            <Empty title="No products match">
              {activeFilters > 0 ? (
                <button type="button" className="btn-link" onClick={() => update({ brand: [], minPrice: null, maxPrice: null, minRating: null, inStock: null })}>
                  Clear the filters
                </button>
              ) : (
                'Try fewer or different words.'
              )}
            </Empty>
          )}
          {data && data.results.content.length > 0 && (
            <>
              <ul className={`grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4 ${result.isFetching ? 'opacity-60' : ''}`}>
                {data.results.content.map((hit) => (
                  <li key={hit.productId}>
                    <ProductTile product={hit} />
                  </li>
                ))}
              </ul>
              <Pager page={data.results.page} totalPages={data.results.totalPages} onPage={(p) => update({ page: String(p) })} />
            </>
          )}
        </div>
      </div>
    </div>
  );
}

interface PanelProps {
  facets: Facets;
  request: SearchParams;
  update: (changes: Record<string, string | string[] | null>) => void;
}

function FacetPanel({ facets, request, update }: PanelProps) {
  const brands = request.brand ?? [];
  return (
    <div className="space-y-6 text-sm">
      {facets.categories.length > 0 && (
        <FacetGroup title="Category">
          {request.categoryId && (
            <button type="button" className="mb-1 block text-left text-ink-soft hover:underline" onClick={() => update({ categoryId: null })}>
              Any category
            </button>
          )}
          {facets.categories.map((c) => (
            <button
              key={c.id}
              type="button"
              aria-pressed={request.categoryId === c.id}
              onClick={() => update({ categoryId: request.categoryId === c.id ? null : c.id })}
              className={`flex w-full justify-between py-0.5 text-left hover:underline ${request.categoryId === c.id ? 'font-bold' : ''}`}
            >
              <span>{c.name}</span>
              <span className="text-ink-faint">{c.count}</span>
            </button>
          ))}
        </FacetGroup>
      )}

      {facets.brands.length > 0 && (
        <FacetGroup title="Brand">
          {facets.brands.map((b) => (
            <label key={b.value} className="flex cursor-pointer items-center gap-2 py-0.5">
              <input
                type="checkbox"
                className="h-4 w-4 accent-ink"
                checked={brands.includes(b.value)}
                onChange={(e) => update({ brand: e.target.checked ? [...brands, b.value] : brands.filter((x) => x !== b.value) })}
              />
              <span className="flex-1">{b.value}</span>
              <span className="text-ink-faint">{b.count}</span>
            </label>
          ))}
        </FacetGroup>
      )}

      <FacetGroup title="Price">
        {facets.priceRanges
          .filter((r) => r.count > 0 || isSelected(request, r.min, r.max))
          .map((r) => {
            const selected = isSelected(request, r.min, r.max);
            return (
              <button
                key={r.label}
                type="button"
                aria-pressed={selected}
                onClick={() =>
                  update(selected ? { minPrice: null, maxPrice: null } : { minPrice: r.min?.toString() ?? null, maxPrice: r.max?.toString() ?? null })
                }
                className={`flex w-full justify-between py-0.5 text-left hover:underline ${selected ? 'font-bold' : ''}`}
              >
                <span>{r.label}</span>
                <span className="text-ink-faint">{r.count}</span>
              </button>
            );
          })}
        <PriceInputs request={request} update={update} />
      </FacetGroup>

      <FacetGroup title="Customer rating">
        {facets.ratings.map((r) => {
          const selected = request.minRating === String(r.minRating);
          return (
            <button
              key={r.minRating}
              type="button"
              aria-pressed={selected}
              onClick={() => update({ minRating: selected ? null : String(r.minRating) })}
              className={`flex w-full justify-between py-0.5 text-left hover:underline ${selected ? 'font-bold' : ''}`}
            >
              <span>{r.label}</span>
              <span className="text-ink-faint">{r.count}</span>
            </button>
          );
        })}
      </FacetGroup>

      <FacetGroup title="Availability">
        <label className="flex cursor-pointer items-center gap-2">
          <input
            type="checkbox"
            className="h-4 w-4 accent-ink"
            checked={request.inStock === 'true'}
            onChange={(e) => update({ inStock: e.target.checked ? 'true' : null })}
          />
          <span className="flex-1">In stock only</span>
          <span className="text-ink-faint">{facets.availability.inStock}</span>
        </label>
      </FacetGroup>
    </div>
  );
}

function isSelected(request: SearchParams, min?: number, max?: number) {
  return (request.minPrice ?? '') === (min?.toString() ?? '') && (request.maxPrice ?? '') === (max?.toString() ?? '') && !!(request.minPrice || request.maxPrice);
}

function PriceInputs({ request, update }: Omit<PanelProps, 'facets'>) {
  const [min, setMin] = useState(request.minPrice ?? '');
  const [max, setMax] = useState(request.maxPrice ?? '');
  return (
    <form
      className="mt-2 flex items-center gap-2"
      onSubmit={(e) => {
        e.preventDefault();
        update({ minPrice: min || null, maxPrice: max || null });
      }}
    >
      <input aria-label="Lowest price" inputMode="numeric" className="field w-20 px-2 py-1.5" placeholder="Min" value={min} onChange={(e) => setMin(e.target.value.replace(/[^\d.]/g, ''))} />
      <input aria-label="Highest price" inputMode="numeric" className="field w-20 px-2 py-1.5" placeholder="Max" value={max} onChange={(e) => setMax(e.target.value.replace(/[^\d.]/g, ''))} />
      <button type="submit" className="btn-quiet px-3 py-1.5">
        Go
      </button>
    </form>
  );
}

function FacetGroup({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <fieldset>
      <legend className="mb-2 font-display text-base font-bold">{title}</legend>
      {children}
    </fieldset>
  );
}

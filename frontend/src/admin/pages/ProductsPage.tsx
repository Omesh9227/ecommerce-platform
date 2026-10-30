import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { adminCatalog } from '../../api/admin';
import { ErrorNote, Loading } from '../../components/Feedback';
import { Pager } from '../../components/Pager';
import { money, percentOff } from '../../lib/format';
import { flattenCategories, PageHead, Pill, Table, Tabs, useParam } from '../ui';

const STATUS: [string, string][] = [
  ['', 'All'],
  ['ACTIVE', 'For sale'],
  ['INACTIVE', 'Hidden'],
  ['DISCONTINUED', 'Discontinued'],
];

export function ProductsPage() {
  const [search, setSearch] = useParam('q');
  const [categoryId, setCategoryId] = useParam('category');
  const [status, setStatus] = useParam('status');
  const [page, setPage] = useParam('page', '0');
  const [text, setText] = useState(search);
  const categories = useQuery({ queryKey: ['categories'], queryFn: adminCatalog.categories });
  const list = useQuery({
    queryKey: ['admin-products', search, categoryId, status, page],
    queryFn: () => adminCatalog.products({ search: search || undefined, categoryId: categoryId || undefined, status: status || undefined, page: Number(page), sort: 'createdAt,desc' }),
    placeholderData: keepPreviousData,
    meta: { private: true },
  });

  return (
    <div>
      <PageHead title="Products" sub={list.data ? `${list.data.totalElements} products` : undefined}>
        <Link to="/admin/products/new" className="btn-buy">
          Add product
        </Link>
      </PageHead>
      <div className="mb-4 flex flex-wrap gap-2">
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            setSearch(text.trim());
          }}
        >
          <input aria-label="Search name, brand or SKU" className="field w-64 py-1.5" placeholder="Name, brand or SKU" value={text} onChange={(e) => setText(e.target.value)} />
          <button type="submit" className="btn-quiet px-3 py-1.5">
            Search
          </button>
        </form>
        <select aria-label="Category" className="field w-auto py-1.5" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
          <option value="">All categories</option>
          {flattenCategories(categories.data ?? []).map((c) => (
            <option key={c.id} value={c.id}>
              {'  '.repeat(c.depth)}
              {c.name}
            </option>
          ))}
        </select>
      </div>
      <Tabs label="Status" value={status} options={STATUS} onChange={setStatus} />
      {list.isLoading && <Loading />}
      {list.isError && <ErrorNote error={list.error} retry={() => list.refetch()} />}
      {list.data && (
        <>
          <Table label="Products" head={['Product', 'SKU', 'Category', 'Price', 'Stock', 'Rating', 'Status']}>
            {list.data.content.map((p) => (
              <tr key={p.id} data-testid="admin-product-row">
                <td className="px-4 py-2.5">
                  <Link to={`/admin/products/${p.id}`} className="font-semibold hover:underline">
                    {p.name}
                  </Link>
                  {p.brand && <span className="block text-xs text-ink-soft">{p.brand}</span>}
                </td>
                <td className="px-4 py-2.5 font-mono text-xs">{p.sku}</td>
                <td className="px-4 py-2.5">{p.category?.name}</td>
                <td className="px-4 py-2.5">
                  {money(p.sellingPrice, p.currency)}
                  {percentOff(p.discountPercent) && <span className="block text-xs text-leaf">{percentOff(p.discountPercent)}</span>}
                </td>
                <td className="px-4 py-2.5">
                  <Link to={`/admin/inventory?product=${p.id}`} className="hover:underline">
                    {p.availableQuantity === 0 ? <Pill tone="bad">Out of stock</Pill> : p.availableQuantity}
                  </Link>
                </td>
                <td className="px-4 py-2.5">{p.reviewCount > 0 ? `${p.averageRating.toFixed(1)} (${p.reviewCount})` : '—'}</td>
                <td className="px-4 py-2.5">
                  <Pill tone={p.status === 'ACTIVE' ? 'good' : p.status === 'INACTIVE' ? 'warn' : 'plain'}>
                    {p.status === 'ACTIVE' ? 'For sale' : p.status === 'INACTIVE' ? 'Hidden' : 'Discontinued'}
                  </Pill>
                </td>
              </tr>
            ))}
          </Table>
          {list.data.content.length === 0 && <p className="mt-4 text-ink-soft">No products match.</p>}
          <Pager page={list.data.page} totalPages={list.data.totalPages} onPage={(p) => setPage(String(p))} />
        </>
      )}
    </div>
  );
}

import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { adminCatalog, type ProductInput } from '../../api/admin';
import { ApiError } from '../../api/client';
import { ErrorNote, Loading, Notice } from '../../components/Feedback';
import { Field } from '../../components/Field';
import { money } from '../../lib/format';
import { ConfirmButton, flattenCategories, PageHead } from '../ui';

type Key = 'sku' | 'name' | 'brand' | 'categoryId' | 'price' | 'discountPercent' | 'description' | 'imageUrls';
interface Form {
  sku: string;
  name: string;
  brand: string;
  categoryId: string;
  price: string;
  discountPercent: string;
  status: 'ACTIVE' | 'INACTIVE';
  description: string;
  imageUrls: string;
}

const EMPTY: Form = { sku: '', name: '', brand: '', categoryId: '', price: '', discountPercent: '0', status: 'ACTIVE', description: '', imageUrls: '' };

/** The same rules as product-service, checked before sending. */
export function validateProduct(f: Form): Partial<Record<Key, string>> {
  const e: Partial<Record<Key, string>> = {};
  if (!/^[A-Za-z0-9-]{3,64}$/.test(f.sku.trim())) e.sku = '3–64 letters, digits or hyphens';
  if (f.name.trim().length < 2 || f.name.trim().length > 200) e.name = '2–200 characters';
  if (f.brand.length > 100) e.brand = 'At most 100 characters';
  if (!f.categoryId) e.categoryId = 'Choose a category';
  const price = Number(f.price);
  if (!f.price || !(price > 0) || !/^\d{1,10}(\.\d{1,2})?$/.test(f.price.trim())) e.price = 'A price above 0, at most 2 decimals';
  const discount = Number(f.discountPercent || 0);
  if (!(discount >= 0 && discount < 100) || !/^\d{1,3}(\.\d{1,2})?$/.test((f.discountPercent || '0').trim())) e.discountPercent = '0 to 99.99';
  if (f.description.length > 5000) e.description = 'At most 5,000 characters';
  const urls = lines(f.imageUrls);
  if (urls.length > 10) e.imageUrls = 'At most 10 photos';
  else if (urls.some((u) => !/^https?:\/\//.test(u) || u.length > 500)) e.imageUrls = 'Each line must be a link starting with http:// or https://';
  return e;
}

function lines(text: string) {
  return text.split('\n').map((l) => l.trim()).filter(Boolean);
}

export function ProductFormPage() {
  const { id } = useParams();
  const isNew = !id || id === 'new';
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<Form>(EMPTY);
  const [errors, setErrors] = useState<Partial<Record<Key, string>>>({});
  const [saved, setSaved] = useState(false);
  const categories = useQuery({ queryKey: ['categories'], queryFn: adminCatalog.categories });
  const product = useQuery({ queryKey: ['admin-product', id], queryFn: () => adminCatalog.product(id!), enabled: !isNew, meta: { private: true } });

  useEffect(() => {
    const p = product.data;
    if (!p) return;
    setForm({
      sku: p.sku,
      name: p.name,
      brand: p.brand ?? '',
      categoryId: p.category?.id ?? '',
      price: String(p.price),
      discountPercent: String(p.discountPercent ?? 0),
      status: p.status === 'INACTIVE' ? 'INACTIVE' : 'ACTIVE',
      description: p.description ?? '',
      imageUrls: (p.imageUrls ?? []).join('\n'),
    });
  }, [product.data]);

  const save = useMutation({
    mutationFn: () => {
      const input: ProductInput = {
        sku: form.sku.trim(),
        name: form.name.trim(),
        brand: form.brand.trim() || undefined,
        description: form.description.trim() || undefined,
        categoryId: form.categoryId,
        price: Number(form.price),
        discountPercent: Number(form.discountPercent || 0),
        status: form.status,
        imageUrls: lines(form.imageUrls),
      };
      return isNew ? adminCatalog.create(input) : adminCatalog.update(id!, input);
    },
    onSuccess: (p) => {
      queryClient.invalidateQueries({ queryKey: ['admin-products'] });
      queryClient.setQueryData(['admin-product', p.id], p);
      setSaved(true);
      if (isNew) navigate(`/admin/products/${p.id}`, { replace: true, state: { created: true } });
    },
    onError: (e) => e instanceof ApiError && setErrors(e.fieldErrors as Partial<Record<Key, string>>),
  });

  const discontinue = useMutation({
    mutationFn: () => adminCatalog.discontinue(id!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-product', id] });
      queryClient.invalidateQueries({ queryKey: ['admin-products'] });
    },
  });

  function set<K extends keyof Form>(key: K, value: Form[K]) {
    setForm((f) => ({ ...f, [key]: value }));
    setSaved(false);
    if (errors[key as Key]) setErrors((e) => ({ ...e, [key]: undefined }));
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const found = validateProduct(form);
    setErrors(found);
    if (Object.keys(found).length === 0) save.mutate();
  }

  if (!isNew && product.isLoading) return <Loading />;
  if (!isNew && product.isError) return <ErrorNote error={product.error} />;
  const discontinued = product.data?.status === 'DISCONTINUED';
  const selling = Number(form.price) * (1 - Number(form.discountPercent || 0) / 100);

  return (
    <div className="max-w-3xl">
      <Link to="/admin/products" className="text-sm text-ink-soft hover:underline">
        All products
      </Link>
      <PageHead title={isNew ? 'Add a product' : form.name || 'Product'}>
        {!isNew && !discontinued && (
          <a href={`/products/${id}`} target="_blank" rel="noreferrer" className="btn-quiet px-3 py-1.5">
            View in shop
          </a>
        )}
      </PageHead>
      {discontinued && <Notice tone="warn">This product is discontinued: customers can’t see or buy it. Saving it as “For sale” brings it back.</Notice>}
      {saved && !isNew && (
        <div className="mb-4">
          <Notice tone="good">
            Saved.{' '}
            <Link to={`/admin/inventory?product=${id}`} className="font-semibold underline">
              Set its stock
            </Link>
          </Notice>
        </div>
      )}
      <form onSubmit={onSubmit} noValidate className="panel mt-4 grid gap-4 p-5 sm:grid-cols-2">
        <Field label="Name" value={form.name} error={errors.name} onChange={(e) => set('name', e.target.value)} className="sm:col-span-2" />
        <Field label="SKU" value={form.sku} error={errors.sku} onChange={(e) => set('sku', e.target.value)} hint="Your stock code, e.g. LAMP-LED-01" />
        <Field label="Brand (optional)" value={form.brand} error={errors.brand} onChange={(e) => set('brand', e.target.value)} />
        <div className="sm:col-span-2">
          <label htmlFor="category" className="mb-1 block text-sm font-semibold">
            Category
          </label>
          <select id="category" className="field" value={form.categoryId} aria-invalid={errors.categoryId ? true : undefined} onChange={(e) => set('categoryId', e.target.value)}>
            <option value="">Choose…</option>
            {flattenCategories(categories.data ?? []).map((c) => (
              <option key={c.id} value={c.id}>
                {c.path}
              </option>
            ))}
          </select>
          {errors.categoryId && <p className="mt-1 text-sm text-chili">{errors.categoryId}</p>}
        </div>
        <Field label="Price (₹)" inputMode="decimal" value={form.price} error={errors.price} onChange={(e) => set('price', e.target.value.replace(/[^\d.]/g, ''))} />
        <Field
          label="Discount (%)"
          inputMode="decimal"
          value={form.discountPercent}
          error={errors.discountPercent}
          onChange={(e) => set('discountPercent', e.target.value.replace(/[^\d.]/g, ''))}
          hint={Number(form.price) > 0 ? `Customers pay ${money(Math.round(selling * 100) / 100)}` : undefined}
        />
        <fieldset className="sm:col-span-2">
          <legend className="mb-1 text-sm font-semibold">Visibility</legend>
          <div className="flex gap-5 text-sm">
            <label className="flex items-center gap-2">
              <input type="radio" name="status" className="accent-ink" checked={form.status === 'ACTIVE'} onChange={() => set('status', 'ACTIVE')} />
              For sale
            </label>
            <label className="flex items-center gap-2">
              <input type="radio" name="status" className="accent-ink" checked={form.status === 'INACTIVE'} onChange={() => set('status', 'INACTIVE')} />
              Hidden from customers
            </label>
          </div>
        </fieldset>
        <div className="sm:col-span-2">
          <label htmlFor="description" className="mb-1 block text-sm font-semibold">
            Description (optional)
          </label>
          <textarea id="description" className="field min-h-28" value={form.description} onChange={(e) => set('description', e.target.value)} />
          {errors.description && <p className="mt-1 text-sm text-chili">{errors.description}</p>}
        </div>
        <div className="sm:col-span-2">
          <label htmlFor="images" className="mb-1 block text-sm font-semibold">
            Photo links, one per line (optional)
          </label>
          <textarea id="images" className="field min-h-20 font-mono text-xs" value={form.imageUrls} onChange={(e) => set('imageUrls', e.target.value)} placeholder="https://…" />
          {errors.imageUrls && <p className="mt-1 text-sm text-chili">{errors.imageUrls}</p>}
        </div>
        {save.isError && Object.keys(errors).length === 0 && (
          <div className="sm:col-span-2">
            <ErrorNote error={save.error} />
          </div>
        )}
        <div className="flex flex-wrap items-center gap-3 sm:col-span-2">
          <button type="submit" className="btn-buy" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : isNew ? 'Add product' : 'Save changes'}
          </button>
          {isNew && <span className="text-sm text-ink-soft">Stock starts at 0; set it on the Inventory page after adding.</span>}
        </div>
      </form>
      {!isNew && !discontinued && (
        <div className="mt-6">
          <ConfirmButton
            label="Discontinue this product"
            className="btn-link text-sm text-chili"
            question="Customers will no longer see it; past orders keep it."
            confirmLabel="Discontinue"
            busy={discontinue.isPending}
            onConfirm={() => discontinue.mutate()}
          />
          {discontinue.isError && <ErrorNote error={discontinue.error} />}
        </div>
      )}
    </div>
  );
}

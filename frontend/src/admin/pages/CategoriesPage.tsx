import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { adminCatalog } from '../../api/admin';
import { ErrorNote, Loading } from '../../components/Feedback';
import { ConfirmButton, flattenCategories, PageHead, type FlatCategory } from '../ui';

export function CategoriesPage() {
  const categories = useQuery({ queryKey: ['categories'], queryFn: adminCatalog.categories });
  const [editing, setEditing] = useState<string | null>(null);
  const flat = flattenCategories(categories.data ?? []);
  return (
    <div className="max-w-4xl">
      <PageHead title="Categories" sub="The shop’s menu and the filters in search come from this tree." />
      <CategoryForm all={flat} />
      {categories.isLoading && <Loading />}
      {categories.isError && <ErrorNote error={categories.error} />}
      <ul className="panel mt-5 divide-y divide-line" aria-label="Category tree">
        {flat.map((c) =>
          editing === c.id ? (
            <li key={c.id} className="p-4">
              <CategoryForm all={flat} existing={c} onDone={() => setEditing(null)} />
            </li>
          ) : (
            <CategoryRow key={c.id} category={c} onEdit={() => setEditing(c.id)} />
          ),
        )}
        {categories.data && flat.length === 0 && <li className="p-4 text-ink-soft">No categories yet. Add the first one above.</li>}
      </ul>
    </div>
  );
}

function CategoryRow({ category, onEdit }: { category: FlatCategory; onEdit: () => void }) {
  const queryClient = useQueryClient();
  const remove = useMutation({
    mutationFn: () => adminCatalog.deleteCategory(category.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['categories'] }),
  });
  return (
    <li className="flex flex-wrap items-center gap-3 px-4 py-3" style={{ paddingLeft: `${1 + category.depth * 1.5}rem` }} data-testid="category-row">
      <div className="min-w-0 flex-1">
        <span className="font-semibold">{category.name}</span>
        {category.description && <span className="block text-xs text-ink-soft">{category.description}</span>}
      </div>
      <Link to={`/admin/products?category=${category.id}`} className="btn-link text-sm">
        Products
      </Link>
      <button type="button" className="btn-link text-sm" onClick={onEdit}>
        Edit
      </button>
      <ConfirmButton
        label="Delete"
        className="btn-link text-sm"
        question={`Delete ${category.name}?`}
        confirmLabel="Delete"
        busy={remove.isPending}
        onConfirm={() => remove.mutate()}
      />
      {remove.isError && (
        <div className="w-full">
          <ErrorNote error={remove.error} />
        </div>
      )}
    </li>
  );
}

function CategoryForm({ all, existing, onDone }: { all: FlatCategory[]; existing?: FlatCategory; onDone?: () => void }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState(existing?.name ?? '');
  const [description, setDescription] = useState(existing?.description ?? '');
  const [parentId, setParentId] = useState(existing?.parentId ?? '');
  const [problem, setProblem] = useState<string | null>(null);
  const save = useMutation({
    mutationFn: () =>
      existing
        ? adminCatalog.updateCategory(existing.id, name.trim(), description.trim() || undefined, parentId || undefined)
        : adminCatalog.createCategory(name.trim(), description.trim() || undefined, parentId || undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      if (!existing) {
        setName('');
        setDescription('');
      }
      onDone?.();
    },
  });
  // A category can't move under itself or its own sub-categories
  const blocked = new Set<string>();
  if (existing) {
    blocked.add(existing.id);
    for (const c of all) if (c.parentId && blocked.has(c.parentId)) blocked.add(c.id);
  }
  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (name.trim().length < 2) return setProblem('A name has 2 to 100 characters.');
    setProblem(null);
    save.mutate();
  }
  return (
    <form onSubmit={onSubmit} className={existing ? 'grid gap-3 sm:grid-cols-[1fr_1fr_14rem_auto]' : 'panel grid gap-3 p-4 sm:grid-cols-[1fr_1fr_14rem_auto]'} aria-label={existing ? `Edit ${existing.name}` : 'Add a category'}>
      <input aria-label="Category name" className="field" placeholder={existing ? 'Name' : 'New category name'} maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
      <input aria-label="Description" className="field" placeholder="Description (optional)" maxLength={500} value={description} onChange={(e) => setDescription(e.target.value)} />
      <select aria-label="Inside" className="field" value={parentId} onChange={(e) => setParentId(e.target.value)}>
        <option value="">Top level</option>
        {all
          .filter((c) => !blocked.has(c.id))
          .map((c) => (
            <option key={c.id} value={c.id}>
              Inside {c.path}
            </option>
          ))}
      </select>
      <div className="flex gap-2">
        <button type="submit" className="btn-dark" disabled={save.isPending}>
          {existing ? 'Save' : 'Add'}
        </button>
        {existing && (
          <button type="button" className="btn-quiet" onClick={onDone}>
            Cancel
          </button>
        )}
      </div>
      {(problem || save.isError) && <div className="sm:col-span-4">{problem ? <p className="text-sm text-chili">{problem}</p> : <ErrorNote error={save.error} />}</div>}
    </form>
  );
}

import type { RecommendationList } from '../api/types';
import { ProductTile } from './ProductTile';

/** A titled shelf of recommended products; it hides itself when it has nothing to show. */
export function ProductRow({ list, title, testId }: { list: RecommendationList | undefined; title?: string; testId?: string }) {
  if (!list || list.products.length === 0) return null;
  return (
    <section className="mt-10" data-testid={testId}>
      <h2 className="mb-3 text-xl font-bold">{title ?? list.title}</h2>
      <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6">
        {list.products.map((p) => (
          <li key={p.productId}>
            <ProductTile product={p} />
          </li>
        ))}
      </ul>
    </section>
  );
}

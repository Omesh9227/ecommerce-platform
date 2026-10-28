import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { wishlist } from '../api/shop';
import type { WishlistItem } from '../api/types';
import { Empty, ErrorNote, Loading, Notice } from '../components/Feedback';
import { Price } from '../components/Price';
import { ProductImage } from '../components/ProductImage';
import { money } from '../lib/format';

export function WishlistPage() {
  const list = useQuery({ queryKey: ['wishlist'], queryFn: wishlist.get, meta: { private: true } });
  if (list.isLoading) return <Loading />;
  if (list.isError) return <ErrorNote error={list.error} retry={() => list.refetch()} />;
  const w = list.data!;
  if (w.items.length === 0) {
    return (
      <Empty title="Your wishlist is empty">
        Save things you like from a product page and we’ll tell you when the price drops.
      </Empty>
    );
  }
  return (
    <div>
      <h1 className="text-3xl font-bold">Wishlist</h1>
      <p className="text-sm text-ink-soft">
        {w.totalItems} of {w.maxItems} saved
      </p>
      {w.warnings.map((warning) => (
        <div key={warning} className="mt-3">
          <Notice tone="warn">{warning}</Notice>
        </div>
      ))}
      <ul className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {w.items.map((item) => (
          <WishlistCard key={item.productId} item={item} currency={w.currency} />
        ))}
      </ul>
    </div>
  );
}

function WishlistCard({ item, currency }: { item: WishlistItem; currency: string }) {
  const queryClient = useQueryClient();
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['wishlist'] });
    queryClient.invalidateQueries({ queryKey: ['wishlist-contains', item.productId] });
  };
  const remove = useMutation({ mutationFn: () => wishlist.remove(item.productId), onSuccess: refresh });
  const move = useMutation({
    mutationFn: () => wishlist.moveToCart(item.productId),
    onSuccess: () => {
      refresh();
      queryClient.invalidateQueries({ queryKey: ['cart'] });
    },
  });
  const busy = remove.isPending || move.isPending;
  return (
    <li className={`panel flex gap-4 p-4 ${busy ? 'opacity-60' : ''}`} data-testid="wishlist-item">
      <Link to={`/products/${item.productId}`} className="h-24 w-24 shrink-0 overflow-hidden rounded bg-mist">
        <ProductImage src={item.imageUrl} name={item.name} className="text-xl" />
      </Link>
      <div className="flex flex-1 flex-col gap-1 text-sm">
        <Link to={`/products/${item.productId}`} className="font-semibold hover:underline">
          {item.name}
        </Link>
        {item.price !== null && <Price amount={item.price} currency={currency} listPrice={item.listPrice} size="sm" />}
        {item.priceDropped && item.priceDifference !== null && (
          <p className="font-semibold text-leaf">Down {money(Math.abs(item.priceDifference), currency)} since you saved it</p>
        )}
        {item.status !== 'AVAILABLE' && item.message && <p className="font-semibold text-chili">{item.message}</p>}
        <div className="mt-auto flex gap-3 pt-2">
          <button type="button" className="btn-buy px-3 py-1.5" disabled={busy || item.status !== 'AVAILABLE'} onClick={() => move.mutate()}>
            Move to cart
          </button>
          <button type="button" className="btn-link" disabled={busy} onClick={() => remove.mutate()}>
            Remove
          </button>
        </div>
        {(move.error || remove.error) && <ErrorNote error={move.error ?? remove.error} />}
      </div>
    </li>
  );
}

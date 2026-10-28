import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { cart as cartApi, wishlist } from '../api/shop';
import type { Cart, CartItem } from '../api/types';
import { Empty, ErrorNote, Loading, Notice } from '../components/Feedback';
import { Price } from '../components/Price';
import { ProductImage } from '../components/ProductImage';
import { money, plural } from '../lib/format';

export function CartPage() {
  const cart = useQuery({ queryKey: ['cart'], queryFn: cartApi.get, meta: { private: true } });

  if (cart.isLoading) return <Loading />;
  if (cart.isError) return <ErrorNote error={cart.error} retry={() => cart.refetch()} />;
  const c = cart.data!;
  if (c.items.length === 0) {
    return (
      <Empty title="Your cart is empty">
        <Link to="/search" className="btn-link">
          Find something to buy
        </Link>
      </Empty>
    );
  }

  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_20rem]">
      <section>
        <h1 className="text-3xl font-bold">Your cart</h1>
        {!c.pricesUpToDate && (
          <div className="mt-4">
            <Notice tone="warn">Prices couldn’t be checked just now; they will be when you place the order.</Notice>
          </div>
        )}
        {c.warnings.length > 0 && (
          <ul className="mt-4 space-y-2">
            {c.warnings.map((w) => (
              <li key={w}>
                <Notice tone="warn">{w}</Notice>
              </li>
            ))}
          </ul>
        )}
        <ul className="mt-4 divide-y divide-line border-y border-line">
          {c.items.map((item) => (
            <CartLine key={item.productId} item={item} currency={c.currency} />
          ))}
        </ul>
      </section>
      <Summary cart={c} />
    </div>
  );
}

function Summary({ cart }: { cart: Cart }) {
  return (
    <aside className="panel h-fit p-5" aria-label="Order summary">
      <p className="text-sm">
        Subtotal ({plural(cart.totalQuantity, 'item')})
      </p>
      <div data-testid="cart-subtotal">
        <Price amount={cart.subtotal} currency={cart.currency} />
      </div>
      {cart.totalSavings > 0 && <p className="mt-1 text-sm font-semibold text-leaf">You save {money(cart.totalSavings, cart.currency)}</p>}
      <p className="mt-3 text-xs text-ink-soft">Any delivery fee is added when you place the order, before you pay.</p>
      {cart.checkoutReady ? (
        <Link to="/checkout" className="btn-buy mt-4 w-full py-3 text-base">
          Proceed to checkout
        </Link>
      ) : (
        <>
          <button type="button" className="btn-buy mt-4 w-full py-3 text-base" disabled>
            Proceed to checkout
          </button>
          <p className="mt-2 text-sm text-chili">Fix the items marked in your cart first.</p>
        </>
      )}
    </aside>
  );
}

function CartLine({ item, currency }: { item: CartItem; currency: string }) {
  const queryClient = useQueryClient();
  const onCart = (cart: Cart) => queryClient.setQueryData(['cart'], cart);
  const setQuantity = useMutation({ mutationFn: (q: number) => cartApi.setQuantity(item.productId, q), onSuccess: onCart });
  const remove = useMutation({ mutationFn: () => cartApi.remove(item.productId), onSuccess: onCart });
  const saveForLater = useMutation({
    mutationFn: async () => {
      await wishlist.add(item.productId); // already saved is fine (200)
      return cartApi.remove(item.productId);
    },
    onSuccess: (cart) => {
      onCart(cart);
      queryClient.invalidateQueries({ queryKey: ['wishlist'] });
    },
  });
  const busy = setQuantity.isPending || remove.isPending || saveForLater.isPending;
  const max = Math.max(item.quantity, Math.min(10, item.availableQuantity ?? 10));
  const error = setQuantity.error ?? remove.error ?? saveForLater.error;

  return (
    <li className={`flex gap-4 py-5 ${busy ? 'opacity-60' : ''}`} data-testid="cart-line">
      <Link to={`/products/${item.productId}`} className="h-28 w-28 shrink-0 overflow-hidden rounded-md bg-white">
        <ProductImage src={item.imageUrl} name={item.name} />
      </Link>
      <div className="flex flex-1 flex-col gap-1">
        <div className="flex flex-wrap justify-between gap-2">
          <Link to={`/products/${item.productId}`} className="font-semibold hover:underline">
            {item.name}
          </Link>
          <Price amount={item.lineTotal} currency={currency} size="sm" />
        </div>
        <p className="text-sm text-ink-soft">
          {money(item.unitPrice, currency)} each
          {item.priceChanged && (
            <span className="ml-2 font-semibold text-ink">
              (was {money(item.priceWhenAdded, currency)} when you added it)
            </span>
          )}
        </p>
        {item.status !== 'AVAILABLE' && item.message && <p className="text-sm font-semibold text-chili">{item.message}</p>}
        <div className="mt-2 flex flex-wrap items-center gap-4 text-sm">
          <label className="flex items-center gap-2">
            Qty
            <select
              className="field w-20 py-1.5"
              value={item.quantity}
              disabled={busy || item.status === 'UNAVAILABLE' || item.status === 'OUT_OF_STOCK'}
              onChange={(e) => setQuantity.mutate(Number(e.target.value))}
            >
              {Array.from({ length: max }, (_, i) => i + 1).map((n) => (
                <option key={n}>{n}</option>
              ))}
            </select>
          </label>
          <button type="button" className="btn-link" disabled={busy} onClick={() => remove.mutate()}>
            Remove
          </button>
          <button type="button" className="btn-link" disabled={busy} onClick={() => saveForLater.mutate()}>
            Save for later
          </button>
        </div>
        {error && (
          <div className="mt-2">
            <ErrorNote error={error} />
          </div>
        )}
      </div>
    </li>
  );
}

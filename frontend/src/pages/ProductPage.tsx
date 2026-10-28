import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { isApiError } from '../api/client';
import { cart as cartApi, catalog, recommendations, shipping, wishlist } from '../api/shop';
import type { Product } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Empty, ErrorNote, Loading } from '../components/Feedback';
import { Price } from '../components/Price';
import { ProductImage } from '../components/ProductImage';
import { ProductRow } from '../components/ProductRow';
import { Stars } from '../components/Stars';
import { storage } from '../lib/storage';
import { rules } from '../lib/validation';
import { Reviews } from './product/Reviews';

export function ProductPage() {
  const { id = '' } = useParams();
  const { user } = useAuth();
  const product = useQuery({ queryKey: ['product', id], queryFn: () => catalog.product(id), retry: (n, e) => !isApiError(e, 404) && n < 2 });
  const boughtTogether = useQuery({ queryKey: ['reco', id, 'bought-together'], queryFn: () => recommendations.forProduct(id, 'bought-together', 6) });
  const similar = useQuery({ queryKey: ['reco', id, 'similar'], queryFn: () => recommendations.forProduct(id, 'similar', 6), retry: false });
  const alsoViewed = useQuery({ queryKey: ['reco', id, 'also-viewed'], queryFn: () => recommendations.forProduct(id, 'also-viewed', 6) });

  // Feeds "recently viewed", "also viewed" and "for you"; a failure here never bothers the shopper
  useEffect(() => {
    if (user && id) recommendations.recordView(id).catch(() => undefined);
  }, [user, id]);

  if (product.isLoading) return <Loading />;
  if (isApiError(product.error, 404) || (product.data && product.data.status !== 'ACTIVE')) {
    return (
      <Empty title="This product isn’t available">
        <Link to="/search" className="btn-link">
          Browse all products
        </Link>
      </Empty>
    );
  }
  if (product.isError) return <ErrorNote error={product.error} retry={() => product.refetch()} />;
  const p = product.data!;

  return (
    <div>
      <nav aria-label="Breadcrumb" className="mb-4 text-sm text-ink-soft">
        <Link to="/" className="hover:underline">
          Home
        </Link>
        {p.category && (
          <>
            {' / '}
            <Link to={`/search?categoryId=${p.category.id}`} className="hover:underline">
              {p.category.name}
            </Link>
          </>
        )}
      </nav>

      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_20rem]">
        <Gallery product={p} />
        <div>
          {p.brand && (
            <Link to={`/search?brand=${encodeURIComponent(p.brand)}`} className="text-sm font-semibold text-ink-soft hover:underline">
              {p.brand}
            </Link>
          )}
          <h1 className="mt-1 text-3xl font-bold leading-tight">{p.name}</h1>
          <a href="#reviews" className="mt-2 inline-block hover:underline">
            <Stars rating={p.averageRating} count={p.reviewCount} size="md" />
          </a>
          <div className="my-5 border-t border-line pt-5">
            <Price amount={p.sellingPrice} currency={p.currency} listPrice={p.price} discountPercent={p.discountPercent} size="lg" />
            <p className="mt-1 text-xs text-ink-soft">Inclusive of all taxes</p>
          </div>
          {p.description && (
            <section>
              <h2 className="mb-2 text-lg font-bold">About this item</h2>
              <p className="max-w-prose whitespace-pre-line leading-relaxed text-ink-soft">{p.description}</p>
            </section>
          )}
          <dl className="mt-5 grid max-w-sm grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-sm">
            <dt className="text-ink-soft">Item code</dt>
            <dd>{p.sku}</dd>
            {p.category && (
              <>
                <dt className="text-ink-soft">Category</dt>
                <dd>{p.category.name}</dd>
              </>
            )}
          </dl>
        </div>
        <BuyBox product={p} />
      </div>

      <ProductRow list={boughtTogether.data} testId="bought-together" />
      <ProductRow list={similar.data} testId="similar" />
      <ProductRow list={alsoViewed.data} testId="also-viewed" />

      <Reviews productId={p.id} />
    </div>
  );
}

function Gallery({ product }: { product: Product }) {
  const images = product.imageUrls?.length ? product.imageUrls : [null];
  const [index, setIndex] = useState(0);
  return (
    <div>
      <div className="aspect-square overflow-hidden rounded-lg bg-white">
        <ProductImage src={images[index]} name={product.name} />
      </div>
      {images.length > 1 && (
        <ul className="mt-3 flex gap-2">
          {images.map((src, i) => (
            <li key={`${src}-${i}`}>
              <button
                type="button"
                aria-label={`Photo ${i + 1}`}
                aria-pressed={i === index}
                onClick={() => setIndex(i)}
                className={`h-16 w-16 overflow-hidden rounded border-2 bg-white ${i === index ? 'border-ink' : 'border-transparent'}`}
              >
                <ProductImage src={src} name={product.name} className="text-base" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function BuyBox({ product }: { product: Product }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const [quantity, setQuantity] = useState(1);
  const [added, setAdded] = useState(false);
  const maxQuantity = Math.max(1, Math.min(10, product.availableQuantity));

  const inWishlist = useQuery({
    queryKey: ['wishlist-contains', product.id],
    queryFn: () => wishlist.contains([product.id]),
    enabled: !!user,
    meta: { private: true },
  });
  const saved = inWishlist.data?.productIds.includes(product.id) ?? false;

  const addToCart = useMutation({
    mutationFn: () => cartApi.add(product.id, quantity),
    onSuccess: (cart) => {
      queryClient.setQueryData(['cart'], cart);
      setAdded(true);
    },
  });

  const toggleWishlist = useMutation({
    mutationFn: () => (saved ? wishlist.remove(product.id) : wishlist.add(product.id)),
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['wishlist-contains', product.id] });
      queryClient.invalidateQueries({ queryKey: ['wishlist'] });
    },
  });

  function needSignIn() {
    navigate('/login', { state: { from: location.pathname } });
  }

  const stock =
    product.availableQuantity <= 0 ? (
      <span className="font-semibold text-chili">Out of stock</span>
    ) : product.availableQuantity <= 5 ? (
      <span className="font-semibold text-chili">Only {product.availableQuantity} left</span>
    ) : (
      <span className="font-semibold text-leaf">In stock</span>
    );

  return (
    <aside className="panel h-fit p-5" aria-label="Buy">
      <Price amount={product.sellingPrice} currency={product.currency} size="md" />
      <p className="mt-2 text-sm" data-testid="stock">
        {stock}
      </p>
      <DeliveryEstimate />
      {product.availableQuantity > 0 && (
        <div className="mt-4 space-y-3">
          <label className="flex items-center gap-3 text-sm">
            <span>Quantity</span>
            <select className="field w-20 py-1.5" value={quantity} onChange={(e) => setQuantity(Number(e.target.value))}>
              {Array.from({ length: maxQuantity }, (_, i) => i + 1).map((n) => (
                <option key={n}>{n}</option>
              ))}
            </select>
          </label>
          <button
            type="button"
            className="btn-buy w-full py-3 text-base"
            disabled={addToCart.isPending}
            onClick={() => (user ? addToCart.mutate() : needSignIn())}
          >
            {addToCart.isPending ? 'Adding…' : 'Add to cart'}
          </button>
          {added && !addToCart.isPending && (
            <p className="text-sm text-leaf" role="status">
              Added to your cart.{' '}
              <Link to="/cart" className="btn-link">
                Go to cart
              </Link>
            </p>
          )}
          {addToCart.isError && <ErrorNote error={addToCart.error} />}
        </div>
      )}
      <button
        type="button"
        className="btn-quiet mt-3 w-full"
        aria-pressed={saved}
        disabled={toggleWishlist.isPending || (!!user && inWishlist.isLoading)}
        onClick={() => (user ? toggleWishlist.mutate() : needSignIn())}
      >
        {saved ? 'Saved to wishlist' : 'Save to wishlist'}
      </button>
      {toggleWishlist.isError && (
        <div className="mt-2">
          <ErrorNote error={toggleWishlist.error} />
        </div>
      )}
    </aside>
  );
}

const PIN_KEY = 'cartwheel.pin';

function DeliveryEstimate() {
  const [pin, setPin] = useState(storage.get(PIN_KEY) ?? '');
  const [asked, setAsked] = useState(storage.get(PIN_KEY));
  const estimate = useQuery({
    queryKey: ['estimate', asked],
    queryFn: () => shipping.estimate(asked!),
    enabled: !!asked && rules.pin.test(asked),
    retry: false,
  });
  const invalid = pin.length > 0 && !rules.pin.test(pin);

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!rules.pin.test(pin)) return;
    storage.set(PIN_KEY, pin);
    setAsked(pin);
  }

  return (
    <form onSubmit={onSubmit} className="mt-4 border-t border-line pt-4">
      <label htmlFor="pin" className="text-sm font-semibold">
        Delivery to PIN code
      </label>
      <div className="mt-1 flex gap-2">
        <input
          id="pin"
          inputMode="numeric"
          maxLength={6}
          className="field py-1.5"
          value={pin}
          aria-invalid={invalid || undefined}
          onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))}
          placeholder="560001"
        />
        <button type="submit" className="btn-quiet px-3 py-1.5" disabled={!rules.pin.test(pin)}>
          Check
        </button>
      </div>
      {invalid && <p className="mt-1 text-xs text-chili">A PIN code has 6 digits and doesn’t start with 0.</p>}
      {estimate.data && (
        <p className="mt-2 text-sm font-semibold text-leaf" data-testid="delivery-estimate">
          {estimate.data.message}
        </p>
      )}
      {estimate.isError && <p className="mt-2 text-sm text-chili">{(estimate.error as Error).message}</p>}
    </form>
  );
}

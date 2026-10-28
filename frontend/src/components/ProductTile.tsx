import { Link } from 'react-router-dom';
import { Price } from './Price';
import { ProductImage } from './ProductImage';
import { Stars } from './Stars';

export interface TileProduct {
  productId: string;
  name: string;
  brand?: string | null;
  imageUrl?: string | null;
  sellingPrice: number;
  price?: number;
  discountPercent?: number;
  currency?: string;
  averageRating?: number | null;
  reviewCount?: number;
  inStock?: boolean;
  reason?: string;
}

/** A product in a grid or a row: photo, name, rating, price. The whole tile is the link. */
export function ProductTile({ product }: { product: TileProduct }) {
  return (
    <Link
      to={`/products/${product.productId}`}
      className="group flex h-full flex-col gap-2 rounded-lg p-2 hover:bg-white focus-visible:bg-white"
    >
      <div className="aspect-square overflow-hidden rounded-md bg-white">
        <ProductImage src={product.imageUrl} name={product.name} />
      </div>
      <div className="flex flex-1 flex-col gap-1 px-0.5">
        {product.brand && <span className="text-xs text-ink-soft">{product.brand}</span>}
        <span className="line-clamp-2 text-sm font-medium leading-snug group-hover:underline">{product.name}</span>
        {product.reviewCount !== undefined && <Stars rating={product.averageRating} count={product.reviewCount} />}
        <div className="mt-auto pt-1">
          <Price
            amount={product.sellingPrice}
            currency={product.currency}
            listPrice={product.price}
            discountPercent={product.discountPercent}
            size="sm"
          />
        </div>
        {product.inStock === false && <span className="text-xs font-semibold text-chili">Out of stock</span>}
        {product.reason && <span className="text-xs text-ink-soft">{product.reason}</span>}
      </div>
    </Link>
  );
}

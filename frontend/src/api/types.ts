// Shapes of the backend's responses (see each service's DTOs). Money arrives as JSON numbers.

export interface ApiEnvelope<T> {
  success: boolean;
  message: string;
  data: T;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

// ---------- user-service ----------

export type Role = 'CUSTOMER' | 'ADMIN';

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  user: { userId: string; email: string; role: Role };
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  password: string;
}

export interface UserProfile {
  userId: string;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  role: Role;
  accountStatus: string;
  createdAt: string;
}

// ---------- product-service ----------

export interface Product {
  id: string;
  sku: string;
  name: string;
  description: string | null;
  brand: string | null;
  category: { id: string; name: string } | null;
  price: number;
  discountPercent: number;
  sellingPrice: number;
  currency: string;
  status: 'ACTIVE' | 'INACTIVE' | 'DISCONTINUED';
  availableQuantity: number;
  inStock: boolean;
  averageRating: number;
  reviewCount: number;
  imageUrls: string[];
}

export interface CategoryNode {
  id: string;
  name: string;
  description: string | null;
  children: CategoryNode[];
}

// ---------- search-service ----------

export interface SearchHit {
  productId: string;
  sku: string;
  name: string;
  brand?: string;
  imageUrl?: string;
  price: number;
  sellingPrice: number;
  discountPercent: number;
  currency: string;
  averageRating: number;
  reviewCount: number;
  inStock: boolean;
  availableQuantity: number;
  categoryId?: string;
  categoryName?: string;
}

export interface Facets {
  brands: { value: string; count: number }[];
  categories: { id: string; name: string; count: number }[];
  priceRanges: { label: string; min?: number; max?: number; count: number }[];
  ratings: { minRating: number; label: string; count: number }[];
  availability: { inStock: number; outOfStock: number };
}

export interface SearchResult {
  query?: string;
  correctedQuery?: string;
  results: Page<SearchHit>;
  facets?: Facets;
}

export interface Suggestions {
  query: string;
  products: { productId: string; name: string; brand: string | null; imageUrl: string | null; sellingPrice: number }[];
  brands: string[];
  categories: { id: string; name: string }[];
  searches: string[];
}

export interface PopularSearch {
  term: string;
  count: number;
}

// ---------- recommendation-service ----------

export interface RecommendedProduct {
  productId: string;
  name: string;
  brand?: string;
  imageUrl?: string;
  sellingPrice: number;
  currency: string;
  averageRating?: number;
  reviewCount: number;
  inStock: boolean;
  categoryId?: string;
  categoryName?: string;
  reason?: string;
}

export interface RecommendationList {
  title: string;
  products: RecommendedProduct[];
}

// ---------- review-service ----------

export interface Review {
  id: string;
  productId: string;
  reviewerName: string;
  rating: number;
  title: string;
  comment?: string;
  verifiedPurchase: boolean;
  helpfulCount: number;
  status: 'VISIBLE' | 'HIDDEN';
  hiddenReason?: string;
  createdAt: string;
  editedAt?: string;
}

export interface RatingSummary {
  productId: string;
  averageRating: number;
  reviewCount: number;
  ratingCounts: Record<string, number>;
}

export interface ReviewInput {
  rating: number;
  title: string;
  comment?: string;
}

// ---------- cart-service ----------

export type CartItemStatus = 'AVAILABLE' | 'INSUFFICIENT_STOCK' | 'OUT_OF_STOCK' | 'UNAVAILABLE' | 'UNKNOWN';

export interface CartItem {
  productId: string;
  sku: string;
  name: string;
  imageUrl: string | null;
  quantity: number;
  unitPrice: number;
  listPrice: number | null;
  priceWhenAdded: number;
  priceChanged: boolean;
  lineTotal: number;
  availableQuantity: number | null;
  status: CartItemStatus;
  message: string | null;
}

export interface Cart {
  items: CartItem[];
  totalQuantity: number;
  subtotal: number;
  totalSavings: number;
  currency: string;
  checkoutReady: boolean;
  pricesUpToDate: boolean;
  warnings: string[];
}

// ---------- order-service ----------

export type OrderStatus =
  | 'CREATED'
  | 'PENDING_PAYMENT'
  | 'CONFIRMED'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'CHECKOUT_FAILED';

export interface Address {
  fullName: string;
  phone: string;
  line1: string;
  line2?: string;
  city: string;
  state: string;
  postalCode: string;
}

export interface OrderItem {
  productId: string;
  sku: string;
  productName: string;
  imageUrl: string | null;
  unitPrice: number;
  listPrice: number | null;
  quantity: number;
  lineTotal: number;
}

export interface TimelineEntry {
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  note: string | null;
  changedAt: string;
}

export interface Order {
  orderId: string;
  orderNumber: string;
  userId?: string;
  status: OrderStatus;
  statusReason: string | null;
  items: OrderItem[];
  itemCount: number;
  subtotal: number;
  shippingFee: number;
  totalAmount: number;
  currency: string;
  shippingAddress: Address & { country?: string };
  paymentDueAt: string | null;
  carrier: string | null;
  trackingNumber: string | null;
  estimatedDeliveryDate: string | null;
  placedAt: string | null;
  confirmedAt: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  cancelledAt: string | null;
  createdAt: string;
  timeline: TimelineEntry[];
}

export interface OrderSummary {
  orderNumber: string;
  status: OrderStatus;
  itemCount: number;
  totalAmount: number;
  currency: string;
  createdAt: string;
  placedAt: string | null;
}

// ---------- payment-service ----------

export type PaymentStatus = 'PENDING' | 'SUCCEEDED' | 'CANCELLED' | 'REFUNDED';

export interface PaymentAttempt {
  attemptId: string;
  method: 'CARD' | 'UPI';
  cardBrand: string | null;
  cardLast4: string | null;
  upiIdMasked: string | null;
  status: 'SUCCEEDED' | 'FAILED';
  failureCode: string | null;
  failureReason: string | null;
  createdAt: string;
}

export interface Payment {
  paymentId: string;
  orderNumber: string;
  status: PaymentStatus;
  amount: number;
  currency: string;
  method: 'CARD' | 'UPI' | null;
  cardBrand: string | null;
  cardLast4: string | null;
  upiIdMasked: string | null;
  paidAt: string | null;
  refundedAt: string | null;
  refundReason: string | null;
  attempts: PaymentAttempt[];
}

export type PayRequest =
  | {
      method: 'CARD';
      card: { number: string; expiryMonth: number; expiryYear: number; cvv: string; holderName?: string };
    }
  | { method: 'UPI'; upiId: string };

// ---------- shipping-service ----------

export type ShipmentStatus =
  | 'PENDING'
  | 'PACKED'
  | 'SHIPPED'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERY_FAILED'
  | 'DELIVERED'
  | 'CANCELLED';

export interface TrackingEvent {
  status: ShipmentStatus;
  location?: string;
  description: string;
  occurredAt: string;
}

export interface Shipment {
  id: string;
  orderNumber: string;
  status: ShipmentStatus;
  statusReason?: string;
  carrier?: string;
  trackingNumber?: string;
  estimatedDeliveryDate?: string;
  failedAttempts: number;
  tracking: TrackingEvent[];
}

export interface Tracking {
  trackingNumber: string;
  carrier: string;
  status: ShipmentStatus;
  estimatedDeliveryDate?: string;
  destination: string;
  shippedAt?: string;
  deliveredAt?: string;
  events: TrackingEvent[];
}

export interface DeliveryEstimate {
  postalCode: string;
  deliveryDays: number;
  estimatedDeliveryDate: string;
  message: string;
}

// ---------- wishlist-service ----------

export interface WishlistItem {
  productId: string;
  sku: string;
  name: string;
  imageUrl: string | null;
  price: number | null;
  listPrice: number | null;
  priceWhenAdded: number;
  priceDifference: number | null;
  priceDropped: boolean;
  availableQuantity: number | null;
  status: 'AVAILABLE' | 'OUT_OF_STOCK' | 'UNAVAILABLE' | 'UNKNOWN';
  message: string | null;
  addedAt: string;
}

export interface Wishlist {
  items: WishlistItem[];
  totalItems: number;
  maxItems: number;
  currency: string;
  pricesUpToDate: boolean;
  warnings: string[];
}

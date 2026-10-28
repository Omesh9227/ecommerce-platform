import { api, newIdempotencyKey } from './client';
import type {
  Address,
  Cart,
  CategoryNode,
  DeliveryEstimate,
  LoginResponse,
  Order,
  OrderStatus,
  OrderSummary,
  Page,
  PayRequest,
  Payment,
  PopularSearch,
  Product,
  RatingSummary,
  RecommendationList,
  RegisterRequest,
  Review,
  ReviewInput,
  SearchResult,
  Shipment,
  Suggestions,
  Tracking,
  UserProfile,
  Wishlist,
} from './types';

// One function per backend call the storefront makes. Paths are the gateway's.

export const auth = {
  login: (email: string, password: string) =>
    api<LoginResponse>('/api/v1/auth/login', { method: 'POST', body: { email, password } }),
  register: (request: RegisterRequest) => api<{ userId: string }>('/api/v1/auth/register', { method: 'POST', body: request }),
  me: () => api<UserProfile>('/api/v1/users/me'),
  updateMe: (profile: { firstName: string; lastName: string; phoneNumber: string }) =>
    api<UserProfile>('/api/v1/users/me', { method: 'PUT', body: profile }),
};

export const catalog = {
  product: (id: string) => api<Product>(`/api/v1/products/${encodeURIComponent(id)}`),
  categories: () => api<CategoryNode[]>('/api/v1/categories'),
};

export interface SearchParams {
  q?: string;
  categoryId?: string;
  brand?: string[];
  minPrice?: string;
  maxPrice?: string;
  minRating?: string;
  inStock?: string;
  sort?: string;
  page?: number;
  size?: number;
}

export const search = {
  products: (params: SearchParams, signal?: AbortSignal) =>
    api<SearchResult>('/api/v1/search/products', { query: { ...params }, signal }),
  suggest: (q: string, signal?: AbortSignal) => api<Suggestions>('/api/v1/search/suggest', { query: { q }, signal }),
  popular: (limit = 8) => api<PopularSearch[]>('/api/v1/search/popular', { query: { limit } }),
};

export const recommendations = {
  trending: (limit = 8, categoryId?: string) =>
    api<RecommendationList>('/api/v1/recommendations/trending', { query: { limit, categoryId } }),
  forYou: (limit = 8) => api<RecommendationList>('/api/v1/recommendations/for-you', { query: { limit } }),
  recentlyViewed: (limit = 8) => api<RecommendationList>('/api/v1/recommendations/recently-viewed', { query: { limit } }),
  clearRecentlyViewed: () => api<null>('/api/v1/recommendations/recently-viewed', { method: 'DELETE' }),
  forProduct: (productId: string, kind: 'bought-together' | 'also-viewed' | 'similar', limit = 8) =>
    api<RecommendationList>(`/api/v1/recommendations/products/${productId}/${kind}`, { query: { limit } }),
  recordView: (productId: string) => api<null>('/api/v1/recommendations/views', { method: 'POST', body: { productId } }),
};

export const reviews = {
  forProduct: (productId: string, params: { rating?: number; verifiedOnly?: boolean; sort?: string; page?: number }) =>
    api<Page<Review>>(`/api/v1/reviews/products/${productId}`, { query: { size: 5, ...params } }),
  summary: (productId: string) => api<RatingSummary>(`/api/v1/reviews/products/${productId}/summary`),
  create: (productId: string, input: ReviewInput) =>
    api<Review>(`/api/v1/reviews/products/${productId}`, { method: 'POST', body: input }),
  mine: (page = 0) => api<Page<Review>>('/api/v1/reviews/me', { query: { page, size: 10 } }),
  edit: (reviewId: string, input: ReviewInput) => api<Review>(`/api/v1/reviews/${reviewId}`, { method: 'PUT', body: input }),
  remove: (reviewId: string) => api<null>(`/api/v1/reviews/${reviewId}`, { method: 'DELETE' }),
  helpful: (reviewId: string) =>
    api<{ helpfulCount: number; votedByMe: boolean }>(`/api/v1/reviews/${reviewId}/helpful`, { method: 'POST' }),
  notHelpful: (reviewId: string) =>
    api<{ helpfulCount: number; votedByMe: boolean }>(`/api/v1/reviews/${reviewId}/helpful`, { method: 'DELETE' }),
};

export const cart = {
  get: () => api<Cart>('/api/v1/cart'),
  add: (productId: string, quantity: number) => api<Cart>('/api/v1/cart/items', { method: 'POST', body: { productId, quantity } }),
  setQuantity: (productId: string, quantity: number) =>
    api<Cart>(`/api/v1/cart/items/${productId}`, { method: 'PUT', body: { quantity } }),
  remove: (productId: string) => api<Cart>(`/api/v1/cart/items/${productId}`, { method: 'DELETE' }),
};

export const orders = {
  /** The key must stay the same if the same checkout is retried (so it can't create two orders). */
  checkout: (shippingAddress: Address, expectedTotal: number | undefined, idempotencyKey: string) =>
    api<Order>('/api/v1/orders/checkout', {
      method: 'POST',
      body: { shippingAddress, expectedTotal },
      headers: { 'Idempotency-Key': idempotencyKey },
    }),
  mine: (page: number, status?: OrderStatus) =>
    api<Page<OrderSummary>>('/api/v1/orders', { query: { page, size: 10, status } }),
  get: (orderNumber: string) => api<Order>(`/api/v1/orders/${encodeURIComponent(orderNumber)}`),
  cancel: (orderNumber: string, reason?: string) =>
    api<Order>(`/api/v1/orders/${encodeURIComponent(orderNumber)}/cancel`, { method: 'POST', body: { reason } }),
};

export const payments = {
  forOrder: (orderNumber: string) => api<Payment>(`/api/v1/payments/orders/${encodeURIComponent(orderNumber)}`),
  pay: (orderNumber: string, request: PayRequest, idempotencyKey = newIdempotencyKey()) =>
    api<Payment>(`/api/v1/payments/orders/${encodeURIComponent(orderNumber)}/pay`, {
      method: 'POST',
      body: request,
      headers: { 'Idempotency-Key': idempotencyKey },
    }),
};

export const shipping = {
  forOrder: (orderNumber: string) => api<Shipment>(`/api/v1/shipments/orders/${encodeURIComponent(orderNumber)}`),
  track: (trackingNumber: string) => api<Tracking>(`/api/v1/shipments/track/${encodeURIComponent(trackingNumber)}`),
  estimate: (postalCode: string) => api<DeliveryEstimate>('/api/v1/shipments/delivery-estimate', { query: { postalCode } }),
};

export const wishlist = {
  get: () => api<Wishlist>('/api/v1/wishlist'),
  add: (productId: string) => api<unknown>('/api/v1/wishlist/items', { method: 'POST', body: { productId } }),
  remove: (productId: string) => api<null>(`/api/v1/wishlist/items/${productId}`, { method: 'DELETE' }),
  contains: (productIds: string[]) =>
    api<{ productIds: string[] }>('/api/v1/wishlist/contains', { query: { productIds: productIds.join(',') } }),
  moveToCart: (productId: string) =>
    api<Wishlist>(`/api/v1/wishlist/items/${productId}/move-to-cart`, { method: 'POST', body: { quantity: 1 } }),
};

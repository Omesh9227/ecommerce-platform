import { api, buildQuery } from './client';
import type { CategoryNode, OrderStatus, OrderSummary, Page, PaymentStatus, Product, Review, ShipmentStatus, TrackingEvent } from './types';

// The staff side: every call needs an ADMIN token (the gateway refuses others with 403).

// ---------- admin-service: dashboard ----------

export interface DashboardSummary {
  from: string;
  to: string;
  currency: string;
  sales: {
    grossSales: number;
    refunds: number;
    netSales: number;
    paidOrders: number;
    averageOrderValue?: number;
    unitsSold: number;
    previousPeriodNetSales: number;
    netSalesChangePercent?: number;
  };
  orders: { placed: number; paid: number; cancelledBeforePayment: number; cancelledAfterPayment: number; shipped: number; delivered: number };
  payments: { succeeded: number; failed: number; successRatePercent?: number; refunds: number };
  customers: { buyers: number; newBuyers: number };
  workload: { ordersToShip: number; oldestToShipPaidAt?: string; ordersInTransit: number; lowStockProducts: number; deliveryFailuresLast7Days: number };
}

export interface DailySales {
  date: string;
  paidOrders: number;
  grossSales: number;
  refunds: number;
  netSales: number;
  unitsSold: number;
}

export interface TopProduct {
  productId: string;
  sku: string;
  name: string;
  unitsSold: number;
  revenue: number;
  orders: number;
}

export interface PipelineStage {
  status: OrderStatus;
  orders: number;
  oldestSince?: string;
}

export interface LowStockItem {
  productId: string;
  sku: string;
  name: string;
  available: number;
  onHand: number;
  reserved: number;
  threshold: number;
  outOfStock: boolean;
  updatedAt: string;
}

export interface ActivityItem {
  occurredAt: string;
  kind: string;
  severity: 'INFO' | 'WARNING';
  message: string;
  orderNumber?: string;
  productId?: string;
}

export interface Range {
  from?: string;
  to?: string;
}

export const dashboard = {
  summary: (range: Range) => api<DashboardSummary>('/api/v1/dashboard/summary', { query: { ...range } }),
  daily: (range: Range) => api<DailySales[]>('/api/v1/dashboard/sales/daily', { query: { ...range } }),
  csvUrl: (range: Range) => '/api/v1/dashboard/sales/daily.csv' + buildQuery({ ...range }),
  top: (range: Range, by: 'revenue' | 'units', limit = 10) =>
    api<TopProduct[]>('/api/v1/dashboard/products/top', { query: { ...range, by, limit } }),
  pipeline: () => api<PipelineStage[]>('/api/v1/dashboard/orders/pipeline'),
  lowStock: (limit = 10) => api<LowStockItem[]>('/api/v1/dashboard/inventory/low-stock', { query: { limit } }),
  activity: (severity: '' | 'INFO' | 'WARNING', page: number, size = 15) =>
    api<Page<ActivityItem>>('/api/v1/dashboard/activity', { query: { severity, page, size } }),
};

// ---------- orders, payments, emails ----------

export interface AdminOrderSummary extends OrderSummary {
  userId: string;
}

export interface PaymentSummary {
  orderNumber: string;
  status: PaymentStatus;
  amount: number;
  currency: string;
  method: 'CARD' | 'UPI' | null;
  paidAt: string | null;
  createdAt: string;
}

export interface EmailSent {
  id: string;
  eventType: string;
  orderNumber: string | null;
  recipient: string;
  subject: string;
  status: string;
  sentAt: string;
}

export const adminOrders = {
  list: (page: number, status?: OrderStatus, userId?: string) =>
    api<Page<AdminOrderSummary>>('/api/v1/orders/admin', { query: { page, size: 20, status, userId, sort: 'createdAt,desc' } }),
  cancel: (orderNumber: string, reason?: string) =>
    api<unknown>(`/api/v1/orders/admin/${encodeURIComponent(orderNumber)}/cancel`, { method: 'POST', body: { reason } }),
  emails: (orderNumber: string) =>
    api<Page<EmailSent>>('/api/v1/notifications/admin', { query: { orderNumber, size: 50 } }),
  payments: (page: number, status?: PaymentStatus) =>
    api<Page<PaymentSummary>>('/api/v1/payments/admin', { query: { page, size: 20, status } }),
};

// ---------- shipping ----------

export interface ShipmentSummary {
  id: string;
  orderNumber: string;
  status: ShipmentStatus;
  carrier?: string;
  trackingNumber?: string;
  estimatedDeliveryDate?: string;
  city?: string;
  postalCode?: string;
  itemCount: number;
  createdAt: string;
  updatedAt?: string;
}

export interface AdminShipment {
  id: string;
  orderNumber: string;
  status: ShipmentStatus;
  statusReason?: string;
  carrier?: string;
  trackingNumber?: string;
  estimatedDeliveryDate?: string;
  failedAttempts: number;
  deliveryAddress?: { recipientName: string; phone: string; line1: string; line2?: string; city: string; state: string; postalCode: string };
  items?: { productId: string; sku: string; productName: string; quantity: number }[];
  tracking: TrackingEvent[];
  createdAt: string;
  userId?: string;
}

export const CARRIERS = ['Delhivery', 'Blue Dart', 'Ecom Express', 'India Post'];

export const adminShipments = {
  list: (page: number, status?: ShipmentStatus, orderNumber?: string) =>
    api<Page<ShipmentSummary>>('/api/v1/shipments/admin', { query: { page, size: 20, status, orderNumber } }),
  get: (id: string) => api<AdminShipment>(`/api/v1/shipments/admin/${id}`),
  pack: (id: string) => api<AdminShipment>(`/api/v1/shipments/admin/${id}/pack`, { method: 'POST' }),
  ship: (id: string, carrier: string, trackingNumber?: string) =>
    api<AdminShipment>(`/api/v1/shipments/admin/${id}/ship`, { method: 'POST', body: { carrier, trackingNumber: trackingNumber || undefined } }),
  scan: (id: string, status: ShipmentStatus, location?: string, note?: string) =>
    api<AdminShipment>(`/api/v1/shipments/admin/${id}/tracking`, {
      method: 'POST',
      body: { status, location: location || undefined, note: note || undefined },
    }),
};

// ---------- catalog ----------

export interface ProductInput {
  sku: string;
  name: string;
  description?: string;
  brand?: string;
  categoryId: string;
  price: number;
  discountPercent: number;
  status: 'ACTIVE' | 'INACTIVE';
  imageUrls: string[];
}

export const adminCatalog = {
  products: (q: { search?: string; categoryId?: string; status?: string; page: number; sort?: string }) =>
    api<Page<Product>>('/api/v1/products', { query: { ...q, size: 20 } }),
  product: (id: string) => api<Product>(`/api/v1/products/${id}`),
  create: (input: ProductInput) => api<Product>('/api/v1/products', { method: 'POST', body: input }),
  update: (id: string, input: ProductInput) => api<Product>(`/api/v1/products/${id}`, { method: 'PUT', body: input }),
  discontinue: (id: string) => api<null>(`/api/v1/products/${id}`, { method: 'DELETE' }),
  categories: () => api<CategoryNode[]>('/api/v1/categories'),
  createCategory: (name: string, description: string | undefined, parentId: string | undefined) =>
    api<{ id: string }>('/api/v1/categories', { method: 'POST', body: { name, description, parentId } }),
  updateCategory: (id: string, name: string, description: string | undefined, parentId: string | undefined) =>
    api<{ id: string }>(`/api/v1/categories/${id}`, { method: 'PUT', body: { name, description, parentId } }),
  deleteCategory: (id: string) => api<null>(`/api/v1/categories/${id}`, { method: 'DELETE' }),
};

// ---------- inventory ----------

export interface StockItem {
  productId: string;
  sku: string;
  productName: string;
  productStatus: string;
  quantityOnHand: number;
  quantityReserved: number;
  availableQuantity: number;
  lowStockThreshold: number;
  lowStock: boolean;
  updatedAt: string;
}

export interface StockMovement {
  id: string;
  movementType: 'ADJUSTMENT' | 'RESTOCK' | 'RESERVE' | 'RELEASE' | 'SALE' | 'RETURN';
  quantityChange: number;
  quantityOnHandAfter: number;
  quantityReservedAfter: number;
  reason: string | null;
  reference: string | null;
  createdAt: string;
}

export const adminInventory = {
  list: (q: { search?: string; lowStock?: boolean; page: number }) =>
    api<Page<StockItem>>('/api/v1/inventory', { query: { ...q, lowStock: q.lowStock || undefined, size: 20 } }),
  get: (productId: string) => api<StockItem>(`/api/v1/inventory/${productId}`),
  setStock: (productId: string, quantityOnHand: number, reason?: string) =>
    api<StockItem>(`/api/v1/inventory/${productId}/stock`, { method: 'PUT', body: { quantityOnHand, reason: reason || undefined } }),
  adjust: (productId: string, quantityChange: number, reason?: string) =>
    api<StockItem>(`/api/v1/inventory/${productId}/stock/adjustments`, { method: 'POST', body: { quantityChange, reason: reason || undefined } }),
  threshold: (productId: string, lowStockThreshold: number) =>
    api<StockItem>(`/api/v1/inventory/${productId}/threshold`, { method: 'PUT', body: { lowStockThreshold } }),
  movements: (productId: string) => api<Page<StockMovement>>(`/api/v1/inventory/${productId}/movements`, { query: { size: 10 } }),
};

// ---------- reviews ----------

export interface AdminReview extends Review {
  userId?: string;
}

export const adminReviews = {
  list: (page: number, status?: 'VISIBLE' | 'HIDDEN', productId?: string) =>
    api<Page<AdminReview>>('/api/v1/reviews/admin', { query: { page, size: 20, status, productId } }),
  hide: (id: string, reason: string) => api<AdminReview>(`/api/v1/reviews/admin/${id}/hide`, { method: 'POST', body: { reason } }),
  unhide: (id: string) => api<AdminReview>(`/api/v1/reviews/admin/${id}/unhide`, { method: 'POST' }),
  remove: (id: string) => api<null>(`/api/v1/reviews/${id}`, { method: 'DELETE' }),
};

// ---------- customers ----------

export type AccountStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'LOCKED';

export interface AdminUser {
  userId: string;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  role: 'CUSTOMER' | 'ADMIN';
  accountStatus: AccountStatus;
  createdAt: string;
}

export const adminUsers = {
  list: (page: number, role?: string, status?: string) =>
    api<Page<AdminUser>>('/api/v1/admin/users', { query: { page, size: 20, role, status, sort: 'createdAt,desc' } }),
  setStatus: (userId: string, status: AccountStatus) =>
    api<AdminUser>(`/api/v1/admin/users/${userId}/status`, { method: 'PUT', body: { status } }),
};

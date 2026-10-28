import type { OrderStatus, ShipmentStatus } from '../api/types';

const ORDER: Record<OrderStatus, [string, string]> = {
  CREATED: ['Being placed', 'bg-mist-deep text-ink'],
  PENDING_PAYMENT: ['Waiting for payment', 'bg-marigold-pale text-ink'],
  CONFIRMED: ['Paid, preparing', 'bg-leaf-pale text-leaf'],
  SHIPPED: ['On its way', 'bg-leaf-pale text-leaf'],
  DELIVERED: ['Delivered', 'bg-leaf text-white'],
  CANCELLED: ['Cancelled', 'bg-mist-deep text-ink-soft'],
  CHECKOUT_FAILED: ['Couldn’t be placed', 'bg-chili-pale text-chili'],
};

const SHIPMENT: Record<ShipmentStatus, string> = {
  PENDING: 'Preparing your package',
  PACKED: 'Packed',
  SHIPPED: 'Shipped',
  IN_TRANSIT: 'In transit',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERY_FAILED: 'Delivery attempt failed',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

export function orderStatusLabel(status: OrderStatus): string {
  return ORDER[status]?.[0] ?? status;
}

export function shipmentStatusLabel(status: ShipmentStatus): string {
  return SHIPMENT[status] ?? status;
}

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  const [label, style] = ORDER[status] ?? [status, 'bg-mist-deep'];
  return <span className={`inline-block rounded px-2 py-0.5 text-xs font-semibold ${style}`}>{label}</span>;
}

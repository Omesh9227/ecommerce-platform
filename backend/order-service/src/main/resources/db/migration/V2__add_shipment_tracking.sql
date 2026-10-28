-- Phase 11: shipping-service now ships and delivers orders and reports it on shipment-events.
-- estimated_delivery_date: from the shipment (shown on the order); shipment_version: the last shipment event applied,
-- so an old or repeated event is ignored.
ALTER TABLE orders ADD COLUMN estimated_delivery_date DATE;
ALTER TABLE orders ADD COLUMN shipment_version BIGINT NOT NULL DEFAULT 0;

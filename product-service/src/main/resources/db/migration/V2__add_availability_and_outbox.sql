-- V2: stock availability (from inventory-service events) + transactional outbox for product events

-- ---------------------------------------------------------------------
-- Availability: a local copy of inventory-service's available quantity, updated from
-- INVENTORY_UPDATED events. inventory_version ignores out-of-date events (-1 = nothing received yet).
-- ---------------------------------------------------------------------
ALTER TABLE products ADD COLUMN available_quantity INTEGER NOT NULL DEFAULT 0;
ALTER TABLE products ADD COLUMN inventory_version  BIGINT  NOT NULL DEFAULT -1;

CREATE INDEX idx_products_status_available ON products (status, available_quantity);

-- ---------------------------------------------------------------------
-- Transactional outbox: events are written in the same transaction as the product change,
-- then a background relay sends them to Kafka.
-- ---------------------------------------------------------------------
CREATE TABLE outbox_events (
    id            UUID           PRIMARY KEY,
    topic         VARCHAR(100)   NOT NULL,
    event_key     VARCHAR(100)   NOT NULL,
    event_type    VARCHAR(50)    NOT NULL,
    payload       VARCHAR(10000) NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL,
    published_at  TIMESTAMPTZ,
    attempts      INTEGER        NOT NULL DEFAULT 0,
    last_error    VARCHAR(500)
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

-- ---------------------------------------------------------------------
-- Backfill: products created before events existed get a PRODUCT_CREATED event,
-- so inventory-service creates stock records for them too.
-- The JSON matches the ProductEvent record exactly.
-- ---------------------------------------------------------------------
INSERT INTO outbox_events (id, topic, event_key, event_type, payload, created_at, attempts)
SELECT gen_random_uuid(),
       'product-events',
       p.id::text,
       'PRODUCT_CREATED',
       json_build_object(
               'eventId',        gen_random_uuid(),
               'eventType',      'PRODUCT_CREATED',
               'occurredAt',     to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
               'productId',      p.id,
               'sku',            p.sku,
               'name',           p.name,
               'status',         p.status,
               'productVersion', p.version
       )::text,
       p.created_at,
       0
FROM products p;

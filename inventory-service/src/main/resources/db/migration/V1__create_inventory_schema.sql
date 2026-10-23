-- V1: schema for inventory-service

-- ---------------------------------------------------------------------
-- One row per product. Created from PRODUCT_CREATED events (never via the API).
-- sku / product_name / product_status / product_version are a read-only snapshot of product-service data,
-- kept up to date from product events. product_version ignores out-of-date events.
-- available = quantity_on_hand - quantity_reserved
-- ---------------------------------------------------------------------
CREATE TABLE inventory_items (
    product_id           UUID          PRIMARY KEY,
    sku                  VARCHAR(64)   NOT NULL,
    product_name         VARCHAR(200)  NOT NULL,
    product_status       VARCHAR(20)   NOT NULL,
    product_version      BIGINT        NOT NULL,
    quantity_on_hand     INTEGER       NOT NULL DEFAULT 0,
    quantity_reserved    INTEGER       NOT NULL DEFAULT 0,
    low_stock_threshold  INTEGER       NOT NULL DEFAULT 5,
    low_stock_alerted    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    version              BIGINT        NOT NULL DEFAULT 0,

    -- The database itself refuses to oversell, even if application code had a bug
    CONSTRAINT chk_inventory_on_hand CHECK (quantity_on_hand >= 0),
    CONSTRAINT chk_inventory_reserved CHECK (quantity_reserved >= 0 AND quantity_reserved <= quantity_on_hand),
    CONSTRAINT chk_inventory_threshold CHECK (low_stock_threshold >= 0)
);

CREATE INDEX idx_inventory_sku ON inventory_items (sku);

-- ---------------------------------------------------------------------
-- Audit trail: every change to stock, who made it and why
-- ---------------------------------------------------------------------
CREATE TABLE stock_movements (
    id                        UUID          PRIMARY KEY,
    product_id                UUID          NOT NULL,
    movement_type             VARCHAR(20)   NOT NULL,
    quantity_change           INTEGER       NOT NULL,
    quantity_on_hand_after    INTEGER       NOT NULL,
    quantity_reserved_after   INTEGER       NOT NULL,
    reason                    VARCHAR(255),
    reference                 VARCHAR(100),
    actor_id                  UUID,
    created_at                TIMESTAMPTZ   NOT NULL,

    CONSTRAINT fk_movements_item FOREIGN KEY (product_id) REFERENCES inventory_items (product_id),
    CONSTRAINT chk_movement_type CHECK (movement_type IN ('ADJUSTMENT', 'RESTOCK', 'RESERVE', 'RELEASE', 'SALE'))
);

CREATE INDEX idx_movements_product_created ON stock_movements (product_id, created_at DESC);

-- ---------------------------------------------------------------------
-- Reservations: stock held for an order until it is confirmed (sold) or released
-- ---------------------------------------------------------------------
CREATE TABLE reservations (
    id               UUID          PRIMARY KEY,
    order_reference  VARCHAR(100)  NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    created_by       UUID,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT uk_reservations_order_reference UNIQUE (order_reference),
    CONSTRAINT chk_reservation_status CHECK (status IN ('RESERVED', 'RELEASED', 'CONFIRMED'))
);

CREATE TABLE reservation_items (
    id               UUID     PRIMARY KEY,
    reservation_id   UUID     NOT NULL,
    product_id       UUID     NOT NULL,
    quantity         INTEGER  NOT NULL,

    CONSTRAINT fk_reservation_items_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id) ON DELETE CASCADE,
    CONSTRAINT fk_reservation_items_item FOREIGN KEY (product_id) REFERENCES inventory_items (product_id),
    CONSTRAINT uk_reservation_items_product UNIQUE (reservation_id, product_id),
    CONSTRAINT chk_reservation_items_quantity CHECK (quantity > 0)
);

-- ---------------------------------------------------------------------
-- Transactional outbox: events are written in the same transaction as the data change,
-- then a background relay sends them to Kafka. Data and events can never disagree.
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

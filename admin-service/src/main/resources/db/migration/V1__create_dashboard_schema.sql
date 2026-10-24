-- admin-service: a reporting copy of orders, payments, stock and deliveries, built only from Kafka events.

-- One row per order, at its latest known state (order-events carry orderVersion; only newer versions are applied)
CREATE TABLE orders (
    order_id         UUID           PRIMARY KEY,
    order_number     VARCHAR(30)    NOT NULL,
    user_id          UUID           NOT NULL,
    status           VARCHAR(30)    NOT NULL,
    total_amount     NUMERIC(12, 2) NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    item_count       INTEGER        NOT NULL,
    placed_at        TIMESTAMPTZ,
    paid_at          TIMESTAMPTZ,
    shipped_at       TIMESTAMPTZ,
    delivered_at     TIMESTAMPTZ,
    cancelled_at     TIMESTAMPTZ,
    cancel_reason    VARCHAR(500),
    refunded_amount  NUMERIC(12, 2),
    refunded_at      TIMESTAMPTZ,
    order_version    BIGINT         NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_orders_paid ON orders (paid_at);
CREATE INDEX idx_orders_placed ON orders (placed_at);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_user ON orders (user_id, paid_at);

-- What each order contained, at the price it was sold for
CREATE TABLE order_items (
    order_id      UUID           NOT NULL REFERENCES orders (order_id) ON DELETE CASCADE,
    product_id    UUID           NOT NULL,
    sku           VARCHAR(64),
    product_name  VARCHAR(255),
    quantity      INTEGER        NOT NULL,
    unit_price    NUMERIC(12, 2) NOT NULL,
    PRIMARY KEY (order_id, product_id)
);

CREATE INDEX idx_order_items_product ON order_items (product_id);

-- Every payment attempt and refund (payment-events), once each
CREATE TABLE payment_events (
    event_id      UUID           PRIMARY KEY,
    event_type    VARCHAR(30)    NOT NULL,
    order_id      UUID,
    order_number  VARCHAR(30),
    amount        NUMERIC(12, 2),
    method        VARCHAR(20),
    reason        VARCHAR(500),
    occurred_at   TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_payment_events_time ON payment_events (occurred_at, event_type);

-- Current stock per product (inventory-events)
CREATE TABLE stock_levels (
    product_id          UUID          PRIMARY KEY,
    sku                 VARCHAR(64),
    product_name        VARCHAR(255),
    quantity_on_hand    INTEGER       NOT NULL,
    quantity_reserved   INTEGER       NOT NULL,
    available_quantity  INTEGER       NOT NULL,
    low_stock_threshold INTEGER       NOT NULL,
    low_stock           BOOLEAN       NOT NULL,
    inventory_version   BIGINT        NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_stock_low ON stock_levels (available_quantity) WHERE low_stock;

-- Missed deliveries (shipment-events), once each
CREATE TABLE delivery_failures (
    event_id      UUID          PRIMARY KEY,
    order_id      UUID,
    order_number  VARCHAR(30),
    carrier       VARCHAR(50),
    reason        VARCHAR(300),
    occurred_at   TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_delivery_failures_time ON delivery_failures (occurred_at);

-- What happened, newest first: the admin's activity feed. One line per event (event_id), kept for app.dashboard.activity-days.
CREATE TABLE activity (
    event_id      UUID          PRIMARY KEY,
    occurred_at   TIMESTAMPTZ   NOT NULL,
    kind          VARCHAR(40)   NOT NULL,
    severity      VARCHAR(10)   NOT NULL,
    message       VARCHAR(500)  NOT NULL,
    order_number  VARCHAR(30),
    product_id    UUID,
    CONSTRAINT ck_activity_severity CHECK (severity IN ('INFO', 'WARNING'))
);

CREATE INDEX idx_activity_recent ON activity (occurred_at DESC);

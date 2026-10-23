-- =====================================================================
-- order-service schema (ecommerce_order_db)
-- =====================================================================

-- One row per checkout. The id is derived from (user id, Idempotency-Key), so the same checkout request can
-- never create two orders - a retry finds the existing row.
CREATE TABLE orders (
    id                  UUID           PRIMARY KEY,
    order_number        VARCHAR(20)    NOT NULL,
    user_id             UUID           NOT NULL,
    user_email          VARCHAR(255),
    idempotency_key     VARCHAR(100)   NOT NULL,
    status              VARCHAR(30)    NOT NULL,
    status_reason       VARCHAR(500),

    currency            VARCHAR(3)     NOT NULL,
    item_count          INTEGER        NOT NULL,
    subtotal            NUMERIC(12, 2) NOT NULL,
    shipping_fee        NUMERIC(12, 2) NOT NULL,
    total_amount        NUMERIC(12, 2) NOT NULL,

    ship_full_name      VARCHAR(100)   NOT NULL,
    ship_phone          VARCHAR(20)    NOT NULL,
    ship_line1          VARCHAR(200)   NOT NULL,
    ship_line2          VARCHAR(200),
    ship_city           VARCHAR(100)   NOT NULL,
    ship_state          VARCHAR(100)   NOT NULL,
    ship_postal_code    VARCHAR(10)    NOT NULL,
    ship_country        VARCHAR(2)     NOT NULL,

    payment_due_at      TIMESTAMPTZ,
    carrier             VARCHAR(100),
    tracking_number     VARCHAR(100),
    placed_at           TIMESTAMPTZ,
    confirmed_at        TIMESTAMPTZ,
    shipped_at          TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,

    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL,
    version             BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uk_orders_order_number UNIQUE (order_number),
    CONSTRAINT uk_orders_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT chk_orders_status CHECK (status IN
        ('CREATED', 'PENDING_PAYMENT', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'CHECKOUT_FAILED')),
    CONSTRAINT chk_orders_amounts CHECK (subtotal >= 0 AND shipping_fee >= 0 AND total_amount = subtotal + shipping_fee),
    CONSTRAINT chk_orders_item_count CHECK (item_count > 0)
);

CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC);
CREATE INDEX idx_orders_status_due ON orders (status, payment_due_at);
CREATE INDEX idx_orders_status_created ON orders (status, created_at);

-- Lines are a copy of the product at checkout time: later price or name changes never alter a placed order.
CREATE TABLE order_items (
    id              UUID           PRIMARY KEY,
    order_id        UUID           NOT NULL,
    product_id      UUID           NOT NULL,
    sku             VARCHAR(64)    NOT NULL,
    product_name    VARCHAR(255)   NOT NULL,
    image_url       VARCHAR(1000),
    unit_price      NUMERIC(12, 2) NOT NULL,
    list_price      NUMERIC(12, 2),
    quantity        INTEGER        NOT NULL,
    line_total      NUMERIC(12, 2) NOT NULL,

    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT uk_order_items_product UNIQUE (order_id, product_id),
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_items_total CHECK (line_total = unit_price * quantity)
);

-- Audit trail: every status change, who made it (null = the system) and why.
CREATE TABLE order_status_history (
    id            UUID          PRIMARY KEY,
    order_id      UUID          NOT NULL,
    from_status   VARCHAR(30),
    to_status     VARCHAR(30)   NOT NULL,
    changed_by    UUID,
    note          VARCHAR(500),
    changed_at    TIMESTAMPTZ   NOT NULL,

    CONSTRAINT fk_history_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE INDEX idx_history_order ON order_status_history (order_id, changed_at);

-- Transactional outbox: events are saved with the order change and relayed to Kafka (order-events).
CREATE TABLE outbox_events (
    id            UUID           PRIMARY KEY,
    topic         VARCHAR(100)   NOT NULL,
    event_key     VARCHAR(100)   NOT NULL,
    event_type    VARCHAR(50)    NOT NULL,
    payload       VARCHAR(20000) NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL,
    published_at  TIMESTAMPTZ,
    attempts      INTEGER        NOT NULL DEFAULT 0,
    last_error    VARCHAR(500)
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

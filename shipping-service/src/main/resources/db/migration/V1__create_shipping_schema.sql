-- shipping-service schema. One shipment per paid order (created from order-service's ORDER_CONFIRMED event).

CREATE TABLE shipments (
    id                      UUID          PRIMARY KEY,
    order_id                UUID          NOT NULL,
    order_number            VARCHAR(30)   NOT NULL,
    user_id                 UUID          NOT NULL,
    user_email              VARCHAR(255),
    status                  VARCHAR(30)   NOT NULL,
    status_reason           VARCHAR(300),
    carrier                 VARCHAR(50),
    tracking_number         VARCHAR(40),
    -- Delivery address, copied from the order
    recipient_name          VARCHAR(100)  NOT NULL,
    recipient_phone         VARCHAR(20)   NOT NULL,
    address_line1           VARCHAR(200)  NOT NULL,
    address_line2           VARCHAR(200),
    city                    VARCHAR(100)  NOT NULL,
    state                   VARCHAR(100)  NOT NULL,
    postal_code             VARCHAR(10)   NOT NULL,
    country                 VARCHAR(2)    NOT NULL,
    estimated_delivery_date DATE          NOT NULL,
    failed_attempts         INTEGER       NOT NULL DEFAULT 0,
    order_version           BIGINT        NOT NULL,
    created_at              TIMESTAMPTZ   NOT NULL,
    packed_at               TIMESTAMPTZ,
    shipped_at              TIMESTAMPTZ,
    delivered_at            TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ   NOT NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_shipments_order UNIQUE (order_id),
    CONSTRAINT uk_shipments_order_number UNIQUE (order_number),
    CONSTRAINT uk_shipments_tracking UNIQUE (tracking_number),
    CONSTRAINT ck_shipments_status CHECK (status IN ('PENDING', 'PACKED', 'SHIPPED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY',
                                                     'DELIVERY_FAILED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_shipments_attempts CHECK (failed_attempts >= 0)
);

CREATE INDEX idx_shipments_user ON shipments (user_id, created_at DESC);
CREATE INDEX idx_shipments_status ON shipments (status, created_at);

CREATE TABLE shipment_items (
    id           UUID          PRIMARY KEY,
    shipment_id  UUID          NOT NULL REFERENCES shipments (id) ON DELETE CASCADE,
    product_id   UUID          NOT NULL,
    sku          VARCHAR(64),
    product_name VARCHAR(255),
    quantity     INTEGER       NOT NULL CHECK (quantity > 0)
);

CREATE INDEX idx_shipment_items_shipment ON shipment_items (shipment_id);

-- The tracking history a customer sees: one row per step or carrier scan
CREATE TABLE tracking_events (
    id           UUID          PRIMARY KEY,
    -- insertion order: lines recorded in the same instant keep the order they happened in
    seq          BIGINT        GENERATED ALWAYS AS IDENTITY,
    shipment_id  UUID          NOT NULL REFERENCES shipments (id) ON DELETE CASCADE,
    status       VARCHAR(30)   NOT NULL,
    location     VARCHAR(100),
    description  VARCHAR(300)  NOT NULL,
    recorded_by  UUID,
    occurred_at  TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_tracking_events_shipment ON tracking_events (shipment_id, occurred_at, seq);

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

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at, id) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_key ON outbox_events (event_key, created_at);

-- =====================================================================
-- wishlist-service schema (ecommerce_wishlist_db)
-- =====================================================================

-- One row per saved product per customer. Name, image and price are a snapshot from when it was saved;
-- the live price and stock come from product-service whenever the wishlist is shown.
CREATE TABLE wishlist_items (
    id                    UUID           PRIMARY KEY,
    user_id               UUID           NOT NULL,
    -- From the customer's token when they saved it; where price-drop emails go
    user_email            VARCHAR(255),
    product_id            UUID           NOT NULL,
    sku                   VARCHAR(64)    NOT NULL,
    product_name          VARCHAR(255)   NOT NULL,
    image_url             VARCHAR(1000),
    price_when_added      NUMERIC(12, 2) NOT NULL,
    -- The price of the last price-drop email for this item: the next email needs a lower price (no repeats)
    last_alerted_price    NUMERIC(12, 2),
    added_at              TIMESTAMPTZ    NOT NULL,

    CONSTRAINT uk_wishlist_user_product UNIQUE (user_id, product_id),
    CONSTRAINT chk_wishlist_price CHECK (price_when_added >= 0)
);

CREATE INDEX idx_wishlist_user ON wishlist_items (user_id, added_at DESC);
-- Price drops look up everyone who saved a product
CREATE INDEX idx_wishlist_product ON wishlist_items (product_id);

-- Latest price seen on product-events, per product. productVersion only goes up, so an old or repeated event
-- (Kafka delivers at least once) is recognised and can't trigger a wrong price-drop email.
CREATE TABLE product_prices (
    product_id            UUID           PRIMARY KEY,
    selling_price         NUMERIC(12, 2),
    status                VARCHAR(20),
    product_version       BIGINT         NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL
);

-- Transactional outbox: events are saved with the data change, then relayed to Kafka
CREATE TABLE outbox_events (
    id            UUID          PRIMARY KEY,
    topic         VARCHAR(100)  NOT NULL,
    event_key     VARCHAR(100)  NOT NULL,
    event_type    VARCHAR(50)   NOT NULL,
    payload       VARCHAR(10000) NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    published_at  TIMESTAMPTZ,
    attempts      INT           NOT NULL DEFAULT 0,
    last_error    VARCHAR(500)
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at, id) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_key ON outbox_events (event_key, created_at);

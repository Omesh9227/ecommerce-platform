-- recommendation-service: a copy of the catalog plus what customers buy and look at, all from events (and the
-- views API). Counters are kept up to date as events arrive, so recommendations are simple, indexed lookups.

CREATE TABLE categories (
    id               UUID          PRIMARY KEY,
    name             VARCHAR(100)  NOT NULL,
    parent_id        UUID,
    category_version BIGINT        NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_categories_parent ON categories (parent_id);

-- One row per product; three sources fill it independently (see search-service for the same pattern).
CREATE TABLE products (
    product_id         UUID           PRIMARY KEY,
    name               VARCHAR(200),
    brand              VARCHAR(100),
    category_id        UUID,
    status             VARCHAR(20),
    selling_price      NUMERIC(12, 2),
    image_url          VARCHAR(500),
    available_quantity INTEGER        NOT NULL DEFAULT 0,
    average_rating     NUMERIC(2, 1)  NOT NULL DEFAULT 0,
    review_count       INTEGER        NOT NULL DEFAULT 0,
    product_version    BIGINT         NOT NULL DEFAULT -1,
    inventory_version  BIGINT         NOT NULL DEFAULT -1,
    rating_version     BIGINT         NOT NULL DEFAULT -1,
    updated_at         TIMESTAMPTZ    NOT NULL
);

-- Only products that can be recommended: for sale and in stock
CREATE INDEX idx_products_available ON products (category_id, average_rating DESC)
    WHERE status = 'ACTIVE' AND available_quantity > 0 AND name IS NOT NULL;

-- Paid orders (ORDER_CONFIRMED). cancelled_at is set if a paid order is cancelled; its counts are then taken back.
CREATE TABLE orders (
    order_id      UUID          PRIMARY KEY,
    order_number  VARCHAR(30)   NOT NULL,
    user_id       UUID          NOT NULL,
    confirmed_at  TIMESTAMPTZ   NOT NULL,
    cancelled_at  TIMESTAMPTZ
);

CREATE INDEX idx_orders_user ON orders (user_id, confirmed_at DESC);

CREATE TABLE order_items (
    order_id    UUID     NOT NULL REFERENCES orders (order_id) ON DELETE CASCADE,
    product_id  UUID     NOT NULL,
    quantity    INTEGER  NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (order_id, product_id)
);

CREATE INDEX idx_order_items_product ON order_items (product_id);

-- "Frequently bought together": in how many paid orders each pair appears (stored both ways: A->B and B->A)
CREATE TABLE bought_together (
    product_id        UUID     NOT NULL,
    other_product_id  UUID     NOT NULL,
    orders            INTEGER  NOT NULL,
    PRIMARY KEY (product_id, other_product_id)
);

-- "Trending": units sold per product per day (UTC)
CREATE TABLE daily_sales (
    product_id  UUID     NOT NULL,
    sale_date   DATE     NOT NULL,
    units       INTEGER  NOT NULL,
    orders      INTEGER  NOT NULL,
    PRIMARY KEY (product_id, sale_date)
);

CREATE INDEX idx_daily_sales_date ON daily_sales (sale_date);

-- What each customer looked at (POST /api/v1/recommendations/views): "recently viewed" and personal recommendations
CREATE TABLE product_views (
    user_id          UUID         NOT NULL,
    product_id       UUID         NOT NULL,
    view_count       INTEGER      NOT NULL,
    first_viewed_at  TIMESTAMPTZ  NOT NULL,
    last_viewed_at   TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (user_id, product_id)
);

CREATE INDEX idx_product_views_recent ON product_views (user_id, last_viewed_at DESC);

-- "Customers who viewed this also viewed": how many customers viewed both (both ways). Anonymous counts.
CREATE TABLE viewed_together (
    product_id        UUID     NOT NULL,
    other_product_id  UUID     NOT NULL,
    customers         INTEGER  NOT NULL,
    PRIMARY KEY (product_id, other_product_id)
);

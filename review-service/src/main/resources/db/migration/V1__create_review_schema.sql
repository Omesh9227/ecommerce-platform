-- =====================================================================
-- review-service schema (ecommerce_review_db)
-- =====================================================================

-- One review per customer per product
CREATE TABLE reviews (
    id                 UUID           PRIMARY KEY,
    product_id         UUID           NOT NULL,
    user_id            UUID           NOT NULL,
    reviewer_name      VARCHAR(100)   NOT NULL,
    rating             INTEGER        NOT NULL,
    title              VARCHAR(150)   NOT NULL,
    comment            VARCHAR(4000),
    verified_purchase  BOOLEAN        NOT NULL DEFAULT FALSE,
    status             VARCHAR(10)    NOT NULL,
    hidden_reason      VARCHAR(300),
    -- Changed only with atomic SQL (+1 / -1 per vote)
    helpful_count      INTEGER        NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ    NOT NULL,
    edited_at          TIMESTAMPTZ,
    version            BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uk_reviews_product_user UNIQUE (product_id, user_id),
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_reviews_status CHECK (status IN ('VISIBLE', 'HIDDEN')),
    CONSTRAINT chk_reviews_helpful CHECK (helpful_count >= 0)
);

CREATE INDEX idx_reviews_product ON reviews (product_id, status, created_at DESC);
CREATE INDEX idx_reviews_user ON reviews (user_id, created_at DESC);

-- "This review was helpful": one vote per customer per review
CREATE TABLE review_votes (
    review_id   UUID         NOT NULL REFERENCES reviews (id) ON DELETE CASCADE,
    user_id     UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (review_id, user_id)
);

-- Who received which product (from ORDER_DELIVERED on order-events): makes their reviews "verified purchase"
CREATE TABLE purchases (
    id            UUID          PRIMARY KEY,
    user_id       UUID          NOT NULL,
    product_id    UUID          NOT NULL,
    order_number  VARCHAR(20)   NOT NULL,
    delivered_at  TIMESTAMPTZ   NOT NULL,

    CONSTRAINT uk_purchases_user_product UNIQUE (user_id, product_id)
);

-- Each product's rating summary over its visible reviews; sent to product-service on review-events
CREATE TABLE product_ratings (
    product_id      UUID          PRIMARY KEY,
    review_count    INTEGER       NOT NULL,
    average_rating  NUMERIC(2, 1) NOT NULL,
    count_1         INTEGER       NOT NULL,
    count_2         INTEGER       NOT NULL,
    count_3         INTEGER       NOT NULL,
    count_4         INTEGER       NOT NULL,
    count_5         INTEGER       NOT NULL,
    rating_version  BIGINT        NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL
);

-- Transactional outbox: events are saved with the data change, then relayed to Kafka
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

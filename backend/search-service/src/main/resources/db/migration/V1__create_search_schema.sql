-- search-service: a read model of the catalog, built only from Kafka events, searched with PostgreSQL full-text
-- search (tsvector + GIN) and trigrams (pg_trgm) for typo correction and fuzzy suggestions.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Category tree (category-events): a category filter includes all its sub-categories
CREATE TABLE categories (
    id               UUID          PRIMARY KEY,
    name             VARCHAR(100)  NOT NULL,
    parent_id        UUID,
    category_version BIGINT        NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_categories_parent ON categories (parent_id);

-- One row per product. Three sources fill it independently, each with its own version, so the events can arrive in
-- any order: product-events (catalog fields), inventory-events (stock), review-events (rating). A row that so far only
-- has stock or a rating (name still NULL) is never shown.
CREATE TABLE search_products (
    product_id         UUID           PRIMARY KEY,
    sku                VARCHAR(64),
    name               VARCHAR(200),
    brand              VARCHAR(100),
    description        VARCHAR(5000),
    category_id        UUID,
    category_name      VARCHAR(100),
    status             VARCHAR(20),
    price              NUMERIC(12, 2),
    selling_price      NUMERIC(12, 2),
    discount_percent   NUMERIC(5, 2),
    image_url          VARCHAR(500),
    listed_at          TIMESTAMPTZ,
    available_quantity INTEGER        NOT NULL DEFAULT 0,
    average_rating     NUMERIC(2, 1)  NOT NULL DEFAULT 0,
    review_count       INTEGER        NOT NULL DEFAULT 0,
    product_version    BIGINT         NOT NULL DEFAULT -1,
    inventory_version  BIGINT         NOT NULL DEFAULT -1,
    rating_version     BIGINT         NOT NULL DEFAULT -1,
    updated_at         TIMESTAMPTZ    NOT NULL,
    -- What a query is matched against. Weights rank matches: A = name, brand, SKU; B = category; C = description.
    -- 'english' stems words (phones -> phone); brand and SKU use 'simple' so they are matched as written.
    search_vector      TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(name, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(brand, '') || ' ' || coalesce(sku, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(category_name, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(description, '')), 'C')
    ) STORED
);

CREATE INDEX idx_search_products_vector ON search_products USING GIN (search_vector);
CREATE INDEX idx_search_products_name_trgm ON search_products USING GIN (lower(name) gin_trgm_ops);
CREATE INDEX idx_search_products_active ON search_products (status) WHERE status = 'ACTIVE';
CREATE INDEX idx_search_products_category ON search_products (category_id);
CREATE INDEX idx_search_products_brand ON search_products (lower(brand));
CREATE INDEX idx_search_products_price ON search_products (selling_price);
CREATE INDEX idx_search_products_rating ON search_products (average_rating DESC, review_count DESC);

-- Every word that appears in a product name, brand or category name: the dictionary for "Did you mean ...?"
CREATE TABLE search_words (
    word VARCHAR(100) PRIMARY KEY
);

CREATE INDEX idx_search_words_trgm ON search_words USING GIN (word gin_trgm_ops);
-- "word LIKE 'pix%'": is a typed word the start of a known word (then it is not a typo)?
CREATE INDEX idx_search_words_prefix ON search_words (word text_pattern_ops);

-- What people search for (normalized text, no user ids): "popular searches" and search-box suggestions
CREATE TABLE search_terms (
    term             VARCHAR(100)  PRIMARY KEY,
    search_count     BIGINT        NOT NULL,
    last_searched_at TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_search_terms_recent ON search_terms (last_searched_at DESC);

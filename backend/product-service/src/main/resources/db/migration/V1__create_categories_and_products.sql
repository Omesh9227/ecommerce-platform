-- V1: catalog schema for product-service

-- ---------------------------------------------------------------------
-- Categories: a tree (Electronics > Mobiles). parent_id is NULL for top-level categories.
-- ---------------------------------------------------------------------
CREATE TABLE categories (
    id           UUID          PRIMARY KEY,
    name         VARCHAR(100)  NOT NULL,
    description  VARCHAR(500),
    parent_id    UUID,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    version      BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id)
);

-- Names are unique among siblings (case-insensitive): two "Accessories" can exist under different parents
CREATE UNIQUE INDEX uk_categories_root_name    ON categories (LOWER(name))            WHERE parent_id IS NULL;
CREATE UNIQUE INDEX uk_categories_sibling_name ON categories (parent_id, LOWER(name)) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_categories_parent ON categories (parent_id);

-- ---------------------------------------------------------------------
-- Products
-- price          = list price (MRP)
-- selling_price  = price after discount; stored so listings can filter and sort by it
-- status         = ACTIVE (visible), INACTIVE (hidden, temporarily), DISCONTINUED (soft-deleted)
-- ---------------------------------------------------------------------
CREATE TABLE products (
    id                UUID           PRIMARY KEY,
    sku               VARCHAR(64)    NOT NULL,
    name              VARCHAR(200)   NOT NULL,
    description       VARCHAR(5000),
    brand             VARCHAR(100),
    category_id       UUID           NOT NULL,
    price             NUMERIC(12, 2) NOT NULL,
    discount_percent  NUMERIC(5, 2)  NOT NULL DEFAULT 0,
    selling_price     NUMERIC(12, 2) NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    created_by        UUID,
    updated_by        UUID,
    created_at        TIMESTAMPTZ    NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL,
    version           BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT chk_products_price CHECK (price > 0),
    CONSTRAINT chk_products_discount CHECK (discount_percent >= 0 AND discount_percent < 100),
    CONSTRAINT chk_products_selling_price CHECK (selling_price >= 0 AND selling_price <= price),
    CONSTRAINT chk_products_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DISCONTINUED'))
);

CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_status_price ON products (status, selling_price);
CREATE INDEX idx_products_status_created ON products (status, created_at DESC);
CREATE INDEX idx_products_brand_lower ON products (LOWER(brand));

-- ---------------------------------------------------------------------
-- Product images: ordered list of URLs (uploads come in a later phase)
-- ---------------------------------------------------------------------
CREATE TABLE product_images (
    product_id  UUID          NOT NULL,
    sort_order  INTEGER       NOT NULL,
    url         VARCHAR(500)  NOT NULL,

    CONSTRAINT pk_product_images PRIMARY KEY (product_id, sort_order),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

-- Ratings: a local copy of review-service's rating summary per product, updated from review-events
-- (PRODUCT_RATING_UPDATED). rating_version comes from review-service and only goes up, so old or repeated events
-- are ignored. Lets product pages show "4.3 (120 reviews)" and lists sort by rating without calling review-service.
ALTER TABLE products ADD COLUMN average_rating NUMERIC(2, 1) NOT NULL DEFAULT 0;
ALTER TABLE products ADD COLUMN review_count   INTEGER       NOT NULL DEFAULT 0;
ALTER TABLE products ADD COLUMN rating_version BIGINT        NOT NULL DEFAULT 0;

CREATE INDEX idx_products_status_rating ON products (status, average_rating DESC);

-- =====================================================================
-- payment-service schema (ecommerce_payment_db)
-- Card numbers and CVVs are NEVER stored: only the brand and the last 4 digits.
-- =====================================================================

-- One payment per order, created from the ORDER_PLACED event (amount comes from the order, never from the client).
CREATE TABLE payments (
    id                   UUID           PRIMARY KEY,
    order_id             UUID           NOT NULL,
    order_number         VARCHAR(20)    NOT NULL,
    user_id              UUID           NOT NULL,
    amount               NUMERIC(12, 2) NOT NULL,
    currency             VARCHAR(3)     NOT NULL,
    status               VARCHAR(20)    NOT NULL,

    method               VARCHAR(10),
    card_brand           VARCHAR(20),
    card_last4           VARCHAR(4),
    upi_id_masked        VARCHAR(100),
    provider_reference   VARCHAR(100),
    paid_at              TIMESTAMPTZ,

    refund_reference     VARCHAR(100),
    refund_reason        VARCHAR(500),
    refunded_at          TIMESTAMPTZ,
    cancelled_at         TIMESTAMPTZ,

    created_at           TIMESTAMPTZ    NOT NULL,
    updated_at           TIMESTAMPTZ    NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uk_payments_order UNIQUE (order_id),
    CONSTRAINT uk_payments_order_number UNIQUE (order_number),
    CONSTRAINT chk_payments_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'CANCELLED', 'REFUNDED')),
    CONSTRAINT chk_payments_amount CHECK (amount > 0)
);

CREATE INDEX idx_payments_user ON payments (user_id, created_at DESC);
CREATE INDEX idx_payments_status ON payments (status, created_at DESC);

-- Every try to pay, successful or not. The Idempotency-Key makes a retried request return the same attempt.
CREATE TABLE payment_attempts (
    id                   UUID           PRIMARY KEY,
    payment_id           UUID           NOT NULL,
    idempotency_key      VARCHAR(100)   NOT NULL,
    method               VARCHAR(10)    NOT NULL,
    card_brand           VARCHAR(20),
    card_last4           VARCHAR(4),
    upi_id_masked        VARCHAR(100),
    amount               NUMERIC(12, 2) NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    failure_code         VARCHAR(50),
    failure_reason       VARCHAR(255),
    provider_reference   VARCHAR(100),
    created_at           TIMESTAMPTZ    NOT NULL,

    CONSTRAINT fk_attempts_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE,
    CONSTRAINT uk_attempts_key UNIQUE (payment_id, idempotency_key),
    CONSTRAINT chk_attempts_status CHECK (status IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_attempts_method CHECK (method IN ('CARD', 'UPI'))
);

CREATE INDEX idx_attempts_payment ON payment_attempts (payment_id, created_at);

-- Transactional outbox: events are saved with the payment change and relayed to Kafka (payment-events).
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

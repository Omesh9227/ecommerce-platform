-- =====================================================================
-- notification-service schema (ecommerce_notification_db)
-- =====================================================================

-- What this service knows about each order, taken from order-events: who to email and what was ordered.
-- Payment events carry neither, so they look the order up here.
CREATE TABLE order_snapshots (
    order_id        UUID           PRIMARY KEY,
    order_number    VARCHAR(20)    NOT NULL,
    user_id         UUID           NOT NULL,
    email           VARCHAR(255),
    total_amount    NUMERIC(12, 2) NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    items_json      VARCHAR(20000) NOT NULL,
    order_version   BIGINT         NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL
);

-- Every email sent. event_id is unique: an event delivered twice by Kafka sends one email.
CREATE TABLE notifications (
    id              UUID           PRIMARY KEY,
    event_id        UUID           NOT NULL,
    event_type      VARCHAR(50)    NOT NULL,
    order_id        UUID,
    order_number    VARCHAR(20),
    user_id         UUID           NOT NULL,
    channel         VARCHAR(10)    NOT NULL,
    recipient       VARCHAR(255)   NOT NULL,
    subject         VARCHAR(255)   NOT NULL,
    status          VARCHAR(10)    NOT NULL,
    sent_at         TIMESTAMPTZ    NOT NULL,

    CONSTRAINT uk_notifications_event UNIQUE (event_id),
    CONSTRAINT chk_notifications_channel CHECK (channel IN ('EMAIL')),
    CONSTRAINT chk_notifications_status CHECK (status IN ('SENT'))
);

CREATE INDEX idx_notifications_user ON notifications (user_id, sent_at DESC);
CREATE INDEX idx_notifications_order ON notifications (order_number);

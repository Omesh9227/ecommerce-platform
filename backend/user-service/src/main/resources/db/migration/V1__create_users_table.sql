-- V1: users table for user-service
-- Emails are normalised to lower-case in the service layer before insert,
-- so a plain UNIQUE constraint is enough to prevent duplicates.

CREATE TABLE users (
    id              UUID            PRIMARY KEY,
    first_name      VARCHAR(50)     NOT NULL,
    last_name       VARCHAR(50)     NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    phone_number    VARCHAR(15)     NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    role            VARCHAR(20)     NOT NULL,
    account_status  VARCHAR(20)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('CUSTOMER', 'ADMIN')),
    CONSTRAINT chk_users_account_status CHECK (account_status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'LOCKED'))
);

-- Admin user listing filters by role/status and sorts by newest first
CREATE INDEX idx_users_role_status ON users (role, account_status);
CREATE INDEX idx_users_created_at ON users (created_at DESC);

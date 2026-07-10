-- =============================================================================
-- V1 — Esquema base de BolsilloApp
-- =============================================================================

-- 1. users
CREATE TABLE users (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email                VARCHAR(255) NOT NULL UNIQUE,
    password_hash        VARCHAR(255) NOT NULL,
    display_name         VARCHAR(100) NOT NULL,
    default_currency     VARCHAR(3)     NOT NULL DEFAULT 'ARS',
    ai_messages_used     INT         NOT NULL DEFAULT 0,
    ai_messages_reset_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_at           TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- 2. refresh_tokens
CREATE TABLE refresh_tokens (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP   NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- 3. categories
CREATE TABLE categories (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    emoji      VARCHAR(10)  NOT NULL,
    color_hex  VARCHAR(7)     NOT NULL,
    type       VARCHAR(10)  NOT NULL CHECK (type IN ('expense', 'income')),
    is_system  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- 4. exchange_rates
CREATE TABLE exchange_rates (
    id            UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    from_currency VARCHAR(3)        NOT NULL,
    to_currency   VARCHAR(3)        NOT NULL,
    rate          NUMERIC(18, 6) NOT NULL,
    rate_date     DATE           NOT NULL,
    source        VARCHAR(20)    NOT NULL,
    created_at    TIMESTAMP      NOT NULL DEFAULT NOW(),
    UNIQUE (from_currency, to_currency, rate_date, source)
);

-- 5. purchases
CREATE TABLE purchases (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    description         VARCHAR(500) NOT NULL,
    total_amount_cents  BIGINT       NOT NULL,
    currency            VARCHAR(3)      NOT NULL DEFAULT 'ARS',
    total_installments  INT          NOT NULL DEFAULT 1,
    category_id         UUID         NOT NULL REFERENCES categories(id),
    purchase_date       DATE         NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 6. transactions
CREATE TABLE transactions (
    id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id       UUID           NOT NULL REFERENCES categories(id),
    type              VARCHAR(10)    NOT NULL CHECK (type IN ('expense', 'income', 'debt')),
    amount_cents      BIGINT         NOT NULL,
    currency          VARCHAR(3)        NOT NULL DEFAULT 'ARS',
    amount_ars_cents  BIGINT         NOT NULL,
    exchange_rate     NUMERIC(18, 6) NOT NULL DEFAULT 1.0,
    description       VARCHAR(500)   NOT NULL,
    transaction_date  DATE           NOT NULL,
    source            VARCHAR(10)    NOT NULL CHECK (source IN ('chat', 'manual', 'import')),
    purchase_id       UUID           REFERENCES purchases(id) ON DELETE SET NULL,
    installment_number INT,
    deleted_at        TIMESTAMP,
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- 7. debts
CREATE TABLE debts (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    counterpart_name VARCHAR(200) NOT NULL,
    direction        VARCHAR(10)  NOT NULL CHECK (direction IN ('owe', 'owed')),
    amount_cents     BIGINT       NOT NULL,
    currency         VARCHAR(3)      NOT NULL DEFAULT 'ARS',
    description      VARCHAR(500),
    due_date         DATE,
    status           VARCHAR(10)  NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'paid')),
    paid_at          TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 8. chat_messages
CREATE TABLE chat_messages (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role                 VARCHAR(10) NOT NULL CHECK (role IN ('user', 'assistant')),
    content              TEXT        NOT NULL,
    intent               VARCHAR(10) CHECK (intent IN ('register', 'query', 'correct', 'chat')),
    transactions_created UUID[],
    model_used           VARCHAR(50),
    tokens_used          INT,
    created_at           TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- 9. user_aliases
CREATE TABLE user_aliases (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    alias       VARCHAR(200) NOT NULL,
    category_id UUID         NOT NULL REFERENCES categories(id),
    use_count   INT          NOT NULL DEFAULT 1,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, alias)
);

-- =============================================================================
-- INDICES
-- =============================================================================

CREATE INDEX idx_transactions_user_id         ON transactions (user_id);
CREATE INDEX idx_transactions_transaction_date ON transactions (transaction_date);
CREATE INDEX idx_transactions_category_id     ON transactions (category_id);
CREATE INDEX idx_transactions_type            ON transactions (type);
CREATE INDEX idx_transactions_deleted_at      ON transactions (deleted_at);

CREATE INDEX idx_chat_messages_user_id        ON chat_messages (user_id);
CREATE INDEX idx_chat_messages_created_at     ON chat_messages (created_at);

CREATE INDEX idx_user_aliases_user_id         ON user_aliases (user_id);
CREATE INDEX idx_user_aliases_alias           ON user_aliases (alias);

CREATE INDEX idx_debts_user_id                ON debts (user_id);
CREATE INDEX idx_debts_status                 ON debts (status);

CREATE INDEX idx_refresh_tokens_token_hash    ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user_id       ON refresh_tokens (user_id);

-- =============================================================================
-- CATEGORIAS DEL SISTEMA
-- =============================================================================

INSERT INTO categories (name, emoji, color_hex, type, is_system) VALUES
    -- Gastos
    ('Comida',          '🍽️', '#E07A3F', 'expense', TRUE),
    ('Supermercado',    '🛒', '#5B8C5A', 'expense', TRUE),
    ('Transporte',      '🚌', '#3F6FE0', 'expense', TRUE),
    ('Salud',           '💊', '#D14373', 'expense', TRUE),
    ('Entretenimiento', '🎮', '#8B5CF6', 'expense', TRUE),
    ('Servicios',       '💡', '#0E9594', 'expense', TRUE),
    ('Ropa',            '👕', '#B8860B', 'expense', TRUE),
    ('Educacion',       '📚', '#4A6FA5', 'expense', TRUE),
    ('Otros',           '📦', '#7A7A7A', 'expense', TRUE),
    -- Ingresos
    ('Sueldo',          '💼', '#2BB673', 'income',  TRUE),
    ('Freelance',       '💻', '#0EA5E9', 'income',  TRUE),
    ('Inversiones',     '📈', '#F59E0B', 'income',  TRUE),
    ('Otros ingresos',  '💰', '#6366F1', 'income',  TRUE);

-- =============================================================================
-- Tablas creadas: 9
-- Categorias del sistema: 13
-- Version: V1
-- =============================================================================

-- =============================================================================
-- V9 — Movimientos fijos: un gasto o ingreso que se carga solo todos los meses
-- el mismo día. Los movimientos que generan llevan source = 'RECURRING'.
-- =============================================================================

CREATE TABLE recurring (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id   UUID         NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    type          VARCHAR(10)  NOT NULL CHECK (type IN ('EXPENSE', 'INCOME')),
    amount_cents  BIGINT       NOT NULL CHECK (amount_cents > 0),
    currency      VARCHAR(3)   NOT NULL DEFAULT 'ARS',
    description   VARCHAR(500) NOT NULL,
    day_of_month  INTEGER      NOT NULL CHECK (day_of_month BETWEEN 1 AND 28),
    next_date     DATE         NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recurring_due ON recurring (next_date);

ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_source_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_source_check
    CHECK (source IN ('CHAT', 'MANUAL', 'IMPORT', 'RECURRING'));

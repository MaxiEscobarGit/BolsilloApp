-- =============================================================================
-- V8 — Presupuestos mensuales por categoría de gasto: un tope en pesos por
-- usuario y categoría. Se borran con el usuario o con la categoría.
-- =============================================================================

CREATE TABLE budgets (
    id           UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id  UUID      NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    amount_cents BIGINT    NOT NULL CHECK (amount_cents > 0),
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, category_id)
);

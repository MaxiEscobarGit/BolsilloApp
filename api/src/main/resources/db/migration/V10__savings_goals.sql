-- =============================================================================
-- V10 — Metas de ahorro: cuánto quiere juntar el usuario, para qué y para
-- cuándo, y cuánto lleva. Los aportes no son movimientos (no son gastos).
-- =============================================================================

CREATE TABLE savings_goals (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    target_cents BIGINT       NOT NULL CHECK (target_cents > 0),
    saved_cents  BIGINT       NOT NULL DEFAULT 0 CHECK (saved_cents >= 0),
    deadline     DATE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_savings_goals_user ON savings_goals (user_id);

-- =============================================================================
-- V7 — Intentos de auth para throttling persistente (login fallido por email+IP,
-- registros por IP). Antes vivía en memoria y se perdía al reiniciar.
-- =============================================================================

CREATE TABLE auth_attempts (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    kind         VARCHAR(20)  NOT NULL CHECK (kind IN ('LOGIN', 'REGISTER')),
    throttle_key VARCHAR(320) NOT NULL,
    attempted_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_auth_attempts_lookup
    ON auth_attempts (kind, throttle_key, attempted_at);

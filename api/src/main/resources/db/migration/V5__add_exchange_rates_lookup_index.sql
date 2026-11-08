-- =============================================================================
-- V5 -- Índice para buscar la cotización más reciente por fuente
-- (fallback a fecha anterior y endpoint /api/exchange-rates/latest)
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_exchange_rates_lookup
    ON exchange_rates (from_currency, to_currency, source, rate_date DESC);

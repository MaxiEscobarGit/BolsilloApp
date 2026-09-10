-- =============================================================================
-- V3 -- Add preferred_exchange_source column to users table
-- =============================================================================

ALTER TABLE users ADD COLUMN preferred_exchange_source VARCHAR(20) NOT NULL DEFAULT 'blue';

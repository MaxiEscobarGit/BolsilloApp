-- =============================================================================
-- V2 -- Fix CHECK constraints to accept uppercase enum values from JPA
-- Order: drop constraints first, then update data, then recreate with uppercase
-- =============================================================================

-- 1. Drop old CHECK constraints first (so UPDATE can write uppercase values)
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_type_check;
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_source_check;
ALTER TABLE debts        DROP CONSTRAINT IF EXISTS debts_direction_check;
ALTER TABLE debts        DROP CONSTRAINT IF EXISTS debts_status_check;
ALTER TABLE categories   DROP CONSTRAINT IF EXISTS categories_type_check;
ALTER TABLE chat_messages DROP CONSTRAINT IF EXISTS chat_messages_intent_check;

-- 2. Update existing data to uppercase
UPDATE transactions  SET type      = UPPER(type)      WHERE type      != UPPER(type);
UPDATE transactions  SET source    = UPPER(source)    WHERE source    != UPPER(source);
UPDATE debts         SET direction = UPPER(direction) WHERE direction != UPPER(direction);
UPDATE debts         SET status    = UPPER(status)    WHERE status    != UPPER(status);
UPDATE categories    SET type      = UPPER(type)      WHERE type      != UPPER(type);
UPDATE chat_messages SET role      = LOWER(role);
UPDATE chat_messages SET intent    = UPPER(intent)    WHERE intent IS NOT NULL AND intent != UPPER(intent);

-- 3. Recreate constraints with uppercase values
ALTER TABLE transactions  ADD CONSTRAINT transactions_type_check   CHECK (type      IN ('EXPENSE', 'INCOME', 'DEBT'));
ALTER TABLE transactions  ADD CONSTRAINT transactions_source_check CHECK (source    IN ('CHAT', 'MANUAL', 'IMPORT'));
ALTER TABLE debts         ADD CONSTRAINT debts_direction_check     CHECK (direction IN ('OWE', 'OWED'));
ALTER TABLE debts         ADD CONSTRAINT debts_status_check        CHECK (status    IN ('PENDING', 'PAID'));
ALTER TABLE categories    ADD CONSTRAINT categories_type_check     CHECK (type      IN ('EXPENSE', 'INCOME'));
ALTER TABLE chat_messages ADD CONSTRAINT chat_messages_intent_check CHECK (intent   IN ('REGISTER', 'QUERY', 'CORRECT', 'CHAT'));

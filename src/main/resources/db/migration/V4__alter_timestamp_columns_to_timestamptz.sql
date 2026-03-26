-- Migrate timestamp columns to TIMESTAMP WITH TIME ZONE (TIMESTAMPTZ).
-- TIMESTAMP stores wall-clock time with no timezone information, which causes
-- incorrect comparisons when the JVM or database timezone changes.
-- Instant (UTC epoch-based) maps correctly to TIMESTAMPTZ.
ALTER TABLE notifications
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN sent_at    TYPE TIMESTAMPTZ USING sent_at    AT TIME ZONE 'UTC',
    ALTER COLUMN failed_at  TYPE TIMESTAMPTZ USING failed_at  AT TIME ZONE 'UTC';

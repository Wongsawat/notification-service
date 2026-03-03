-- Add composite index for status + createdAt queries
-- Improves performance for queries filtering by status and sorting by creation time
CREATE INDEX idx_notifications_status_created_at
ON notifications(status, created_at DESC);

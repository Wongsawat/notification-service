-- Outbox pattern table for reliable event publishing via Debezium CDC
-- Follows Transactional Outbox Pattern:
-- 1. Business operation + event persist in same transaction (atomicity)
-- 2. Debezium CDC monitors this table and publishes to Kafka
-- 3. Guarantees at-least-once delivery with no message loss

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    topic VARCHAR(255),
    partition_key VARCHAR(255),
    headers TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER DEFAULT 0,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP
);

-- Index for OutboxService polling PENDING events
CREATE INDEX idx_outbox_status ON outbox_events(status);

-- Index for chronological processing
CREATE INDEX idx_outbox_created ON outbox_events(created_at);

-- Partial index optimized for Debezium CDC polling (only PENDING events)
CREATE INDEX idx_outbox_debezium ON outbox_events(created_at) WHERE status = 'PENDING';

-- Index for querying events by aggregate (e.g., all events for notification X)
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_id, aggregate_type);

-- Comments for documentation
COMMENT ON TABLE outbox_events IS 'Transactional outbox for reliable event publishing via Debezium CDC';
COMMENT ON COLUMN outbox_events.aggregate_type IS 'Business entity type (e.g., Notification, Invoice)';
COMMENT ON COLUMN outbox_events.aggregate_id IS 'Business entity ID (e.g., notification UUID)';
COMMENT ON COLUMN outbox_events.event_type IS 'Event class name (e.g., NotificationSentEvent)';
COMMENT ON COLUMN outbox_events.payload IS 'JSON serialized event payload (TEXT for portability)';
COMMENT ON COLUMN outbox_events.topic IS 'Target Kafka topic for Debezium EventRouter';
COMMENT ON COLUMN outbox_events.partition_key IS 'Kafka partition key for ordering guarantees';
COMMENT ON COLUMN outbox_events.headers IS 'Additional Kafka headers as JSON';
COMMENT ON COLUMN outbox_events.status IS 'Publication status: PENDING, PUBLISHED, or FAILED';

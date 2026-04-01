-- Consolidated baseline schema for notification_db
-- Includes notifications table, outbox_events table, and document_intake_stats table
-- Fresh installation only (incremental V1-V5 migrations replaced by this single baseline)

-- Create notifications table
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    recipient VARCHAR(500) NOT NULL,
    subject VARCHAR(500),
    body TEXT,
    metadata TEXT,
    template_name VARCHAR(100),
    template_variables TEXT,
    document_id VARCHAR(100),
    document_number VARCHAR(100),
    correlation_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT
);

-- Indexes for common queries
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_document_id ON notifications(document_id);
CREATE INDEX idx_notifications_document_number ON notifications(document_number);
CREATE INDEX idx_notifications_recipient ON notifications(recipient);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);
CREATE INDEX idx_notifications_type ON notifications(type);
CREATE INDEX idx_notifications_channel ON notifications(channel);
CREATE INDEX idx_notifications_status_created_at ON notifications(status, created_at DESC);
CREATE INDEX idx_notifications_failed_retry ON notifications(status, retry_count) WHERE status = 'FAILED';

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
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ
);

-- Outbox indexes
CREATE INDEX idx_outbox_status ON outbox_events(status);
CREATE INDEX idx_outbox_created ON outbox_events(created_at);
CREATE INDEX idx_outbox_debezium ON outbox_events(created_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_id, aggregate_type);

-- Document intake statistics table
CREATE TABLE document_intake_stats (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     VARCHAR(255) NOT NULL,
    document_type   VARCHAR(100),
    document_number VARCHAR(255),
    status          VARCHAR(50)  NOT NULL,
    source          VARCHAR(100),
    correlation_id  VARCHAR(255),
    occurred_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_intake_stats_document_id    ON document_intake_stats (document_id);
CREATE INDEX idx_intake_stats_status         ON document_intake_stats (status);
CREATE INDEX idx_intake_stats_document_type  ON document_intake_stats (document_type);
CREATE INDEX idx_intake_stats_occurred_at    ON document_intake_stats (occurred_at);

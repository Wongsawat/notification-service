-- Consolidated migration: notifications + outbox tables
-- Fresh installation only (V2-V4 deleted)

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

-- Create indexes for common queries
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_document_id ON notifications(document_id);
CREATE INDEX idx_notifications_document_number ON notifications(document_number);
CREATE INDEX idx_notifications_recipient ON notifications(recipient);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);
CREATE INDEX idx_notifications_type ON notifications(type);
CREATE INDEX idx_notifications_channel ON notifications(channel);
CREATE INDEX idx_notifications_status_created_at ON notifications(status, created_at DESC);

-- Create index for failed notifications retry query
CREATE INDEX idx_notifications_failed_retry ON notifications(status, retry_count) WHERE status = 'FAILED';

-- Comments
COMMENT ON TABLE notifications IS 'Notification records for email, SMS, and webhook notifications';
COMMENT ON COLUMN notifications.metadata IS 'Additional metadata as JSON text';
COMMENT ON COLUMN notifications.template_variables IS 'Template variable values as JSON text';
COMMENT ON COLUMN notifications.retry_count IS 'Number of retry attempts';

-- Outbox pattern table for reliable event publishing via Debezium CDC
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

COMMENT ON TABLE outbox_events IS 'Transactional outbox for reliable event publishing via Debezium CDC';

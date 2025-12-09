-- Create notifications table
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    recipient VARCHAR(500) NOT NULL,
    subject VARCHAR(500),
    body TEXT,
    metadata JSONB,
    template_name VARCHAR(100),
    template_variables JSONB,
    invoice_id VARCHAR(100),
    invoice_number VARCHAR(100),
    correlation_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP,
    failed_at TIMESTAMP,
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT
);

-- Create indexes for common queries
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_invoice_id ON notifications(invoice_id);
CREATE INDEX idx_notifications_invoice_number ON notifications(invoice_number);
CREATE INDEX idx_notifications_recipient ON notifications(recipient);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);
CREATE INDEX idx_notifications_type ON notifications(type);
CREATE INDEX idx_notifications_channel ON notifications(channel);

-- Create index for failed notifications retry query
CREATE INDEX idx_notifications_failed_retry ON notifications(status, retry_count) WHERE status = 'FAILED';

-- Comments
COMMENT ON TABLE notifications IS 'Notification records for email, SMS, and webhook notifications';
COMMENT ON COLUMN notifications.metadata IS 'Additional metadata as JSON';
COMMENT ON COLUMN notifications.template_variables IS 'Template variable values as JSON';
COMMENT ON COLUMN notifications.retry_count IS 'Number of retry attempts';

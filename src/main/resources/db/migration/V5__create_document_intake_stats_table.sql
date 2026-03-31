CREATE TABLE IF NOT EXISTS document_intake_stats (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     VARCHAR(255) NOT NULL,
    document_type   VARCHAR(100),
    document_number VARCHAR(255),
    status          VARCHAR(50)  NOT NULL,
    source          VARCHAR(100),
    correlation_id  VARCHAR(255),
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_intake_stats_document_id    ON document_intake_stats (document_id);
CREATE INDEX IF NOT EXISTS idx_intake_stats_status         ON document_intake_stats (status);
CREATE INDEX IF NOT EXISTS idx_intake_stats_document_type  ON document_intake_stats (document_type);
CREATE INDEX IF NOT EXISTS idx_intake_stats_occurred_at    ON document_intake_stats (occurred_at);
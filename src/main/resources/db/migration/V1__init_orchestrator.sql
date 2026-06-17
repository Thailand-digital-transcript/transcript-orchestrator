-- V1: Initial schema for transcript-orchestrator service
-- Tables: batches, transcript_items, outbox_events
-- M4 fix: batches table includes updated_at column with index for stuck-phase sweeper.

CREATE TABLE batches (
    id                       UUID         PRIMARY KEY,
    name                     VARCHAR(255) NOT NULL,
    institution_code         VARCHAR(50)  NOT NULL,
    status                   VARCHAR(30)  NOT NULL,
    awaiting_reply_for       VARCHAR(100),
    version                  BIGINT       NOT NULL DEFAULT 0,
    item_count               INTEGER      NOT NULL DEFAULT 0,
    created_by               VARCHAR(100) NOT NULL,
    closed_by                VARCHAR(100),
    closed_at                TIMESTAMPTZ,
    registrar_approved_by    VARCHAR(100),
    registrar_approved_at    TIMESTAMPTZ,
    dean_approved_by         VARCHAR(100),
    dean_approved_at         TIMESTAMPTZ,
    rejected_by              VARCHAR(100),
    rejected_at              TIMESTAMPTZ,
    rejection_reason         TEXT,
    failure_reason           TEXT,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at             TIMESTAMPTZ
);
CREATE INDEX idx_batch_status         ON batches(status);
CREATE INDEX idx_batch_institution    ON batches(institution_code);
CREATE INDEX idx_batch_awaiting       ON batches(awaiting_reply_for) WHERE awaiting_reply_for IS NOT NULL;
CREATE INDEX idx_batch_updated_at     ON batches(updated_at);

CREATE TABLE transcript_items (
    id                       UUID         PRIMARY KEY,
    transcript_id            VARCHAR(100) NOT NULL,
    document_id              VARCHAR(100) NOT NULL,
    institution_code         VARCHAR(50)  NOT NULL,
    transcript_type          VARCHAR(50),
    original_xml_storage_key VARCHAR(500),
    status                   VARCHAR(30)  NOT NULL,
    batch_id                 UUID         REFERENCES batches(id),
    registrar_signed_xml_key VARCHAR(500),
    dean_signed_xml_key      VARCHAR(500),
    sealed_xml_key           VARCHAR(500),
    pdf_key                  VARCHAR(500),
    signed_pdf_key           VARCHAR(500),
    rejection_reason         TEXT,
    failure_reason           TEXT,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX idx_item_transcript ON transcript_items(transcript_id);
CREATE        INDEX idx_item_batch     ON transcript_items(batch_id);
CREATE        INDEX idx_item_status    ON transcript_items(status);

CREATE TABLE outbox_events (
    id              UUID         PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    retry_count     INTEGER      NOT NULL DEFAULT 0,
    error_message   TEXT,
    topic           VARCHAR(255),
    partition_key   VARCHAR(255),
    headers         TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMPTZ
);
CREATE INDEX idx_outbox_status  ON outbox_events(status);
CREATE INDEX idx_outbox_created ON outbox_events(created_at);

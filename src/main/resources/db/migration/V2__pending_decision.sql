-- V2: approval-decision audit + idempotency record.
-- Written atomically with the outbox event by the decision endpoint;
-- the consumer claims a decision via a conditional UPDATE on processed_at.
CREATE TABLE pending_decision (
    decision_id           UUID         PRIMARY KEY,
    batch_id              UUID         NOT NULL,
    gate                  VARCHAR(30)  NOT NULL,   -- PENDING_REGISTRAR | PENDING_DEAN
    decision              VARCHAR(10)  NOT NULL,   -- APPROVE | REJECT
    approved_by           VARCHAR(150) NOT NULL,
    institution_code      VARCHAR(50)  NOT NULL,
    rejection_reason      TEXT,
    rejected_document_ids TEXT,                    -- JSON array, for forensics
    outbox_event_id       UUID         REFERENCES outbox_events(id),
    processed_at          TIMESTAMPTZ,             -- dedupe marker (NULL = unclaimed)
    created_at            TIMESTAMPTZ  NOT NULL    -- always set by the entity (PendingDecisionEntity.fromDomain); no DB default to avoid two clocks
);
CREATE INDEX idx_pending_decision_batch_gate ON pending_decision(batch_id, gate);

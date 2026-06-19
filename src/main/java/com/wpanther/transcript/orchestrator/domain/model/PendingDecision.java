package com.wpanther.transcript.orchestrator.domain.model;

import java.util.List;
import java.util.UUID;

/** Audit + idempotency record for a human approval/rejection decision. */
public record PendingDecision(
        UUID decisionId,
        UUID batchId,
        BatchStatus gate,
        String decision,            // APPROVE | REJECT
        String approvedBy,
        String institutionCode,
        String rejectionReason,     // nullable
        List<String> rejectedDocumentIds,
        UUID outboxEventId          // nullable until publish
) {
    public PendingDecision withOutboxEventId(UUID id) {
        return new PendingDecision(decisionId, batchId, gate, decision, approvedBy,
                institutionCode, rejectionReason, rejectedDocumentIds, id);
    }
}

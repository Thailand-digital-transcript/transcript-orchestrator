package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.transcript.orchestrator.domain.model.BatchStatus;
import com.wpanther.transcript.orchestrator.domain.model.PendingDecision;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "pending_decision")
public class PendingDecisionEntity {
    private static final ObjectMapper M = new ObjectMapper();

    @Id @Column(name = "decision_id") private UUID decisionId;
    @Column(name = "batch_id", nullable = false) private UUID batchId;
    @Column(name = "gate", nullable = false) private String gate;
    @Column(name = "decision", nullable = false) private String decision;
    @Column(name = "approved_by", nullable = false) private String approvedBy;
    @Column(name = "institution_code", nullable = false) private String institutionCode;
    @Column(name = "rejection_reason") private String rejectionReason;
    @Column(name = "rejected_document_ids") private String rejectedDocumentIds; // JSON
    @Column(name = "outbox_event_id") private UUID outboxEventId;
    @Column(name = "processed_at") private Instant processedAt;
    @Column(name = "created_at") private Instant createdAt;

    protected PendingDecisionEntity() {}

    static PendingDecisionEntity fromDomain(PendingDecision d) {
        PendingDecisionEntity e = new PendingDecisionEntity();
        e.decisionId = d.decisionId();
        e.batchId = d.batchId();
        e.gate = d.gate().name();
        e.decision = d.decision();
        e.approvedBy = d.approvedBy();
        e.institutionCode = d.institutionCode();
        e.rejectionReason = d.rejectionReason();
        e.rejectedDocumentIds = writeJson(d.rejectedDocumentIds());
        e.outboxEventId = d.outboxEventId();
        e.createdAt = Instant.now();
        return e;
    }

    PendingDecision toDomain() {
        return new PendingDecision(decisionId, batchId, BatchStatus.valueOf(gate), decision,
                approvedBy, institutionCode, rejectionReason, readJson(rejectedDocumentIds), outboxEventId);
    }

    private static String writeJson(List<String> v) {
        try { return M.writeValueAsString(v == null ? List.of() : v); }
        catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
    @SuppressWarnings("unchecked")
    private static List<String> readJson(String s) {
        if (s == null || s.isBlank()) return List.of();
        try { return M.readValue(s, List.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
}

package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.transcript.saga.domain.model.InboundCommand;
import java.time.Instant;
import java.util.List;

/**
 * Approval/rejection command published (via outbox) to approval.registrar /
 * approval.dean. Extends InboundCommand (saga-commons) so OutboxService accepts
 * it; field names match RegistrarApprovalEvent/DeanApprovalEvent so the existing
 * ApprovalConsumerRoute deserializes it unchanged (it ignores unknown envelope
 * fields from IntegrationEvent).
 */
public class OutboundApprovalCommand extends InboundCommand {
    private static final long serialVersionUID = 1L;

    @JsonProperty("decisionId")          private final String decisionId;
    @JsonProperty("batchId")             private final String batchId;
    @JsonProperty("decision")            private final String decision;
    @JsonProperty("institutionCode")     private final String institutionCode;
    @JsonProperty("approvedBy")          private final String approvedBy;
    @JsonProperty("approvedAt")          private final Instant approvedAt;
    @JsonProperty("rejectedDocumentIds") private final List<String> rejectedDocumentIds;
    @JsonProperty("rejectionReason")     private final String rejectionReason;

    public OutboundApprovalCommand(String decisionId, String batchId, String decision,
            String institutionCode, String approvedBy, Instant approvedAt,
            List<String> rejectedDocumentIds, String rejectionReason) {
        // InboundCommand(documentId, source, correlationId, documentType).
        // documentType normally selects a saga flow, but an approval only advances
        // an existing batch (the ApprovalConsumerRoute does not read it), so we tag
        // it "APPROVAL_DECISION" rather than imply a new TRANSCRIPT_BATCH saga start.
        super(batchId, "approval-ui", batchId, "APPROVAL_DECISION");
        this.decisionId = decisionId;
        this.batchId = batchId;
        this.decision = decision;
        this.institutionCode = institutionCode;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.rejectedDocumentIds = rejectedDocumentIds == null ? List.of() : rejectedDocumentIds;
        this.rejectionReason = rejectionReason;
    }

    public String getDecisionId()              { return decisionId; }
    public String getBatchId()                 { return batchId; }
    public String getDecision()                { return decision; }
    public String getInstitutionCode()         { return institutionCode; }
    public String getApprovedBy()              { return approvedBy; }
    public Instant getApprovedAt()             { return approvedAt; }
    public List<String> getRejectedDocumentIds() { return rejectedDocumentIds; }
    public String getRejectionReason()         { return rejectionReason; }
}

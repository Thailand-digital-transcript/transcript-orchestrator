package com.wpanther.transcript.orchestrator.application.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import java.time.Instant; import java.util.List;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeanApprovalEvent {
    private final String decisionId;          // null for legacy/event-driven paths
    private final String batchId;
    private final String decision;             // "APPROVE" | "REJECT"
    private final String institutionCode;
    private final String approvedBy;
    private final Instant approvedAt;
    private final List<String> rejectedDocumentIds;
    private final String rejectionReason;      // null for APPROVE

    @JsonCreator
    public DeanApprovalEvent(
            @JsonProperty("decisionId")           String decisionId,
            @JsonProperty("batchId")              String batchId,
            @JsonProperty("decision")             String decision,
            @JsonProperty("institutionCode")      String institutionCode,
            @JsonProperty("approvedBy")           String approvedBy,
            @JsonProperty("approvedAt")           Instant approvedAt,
            @JsonProperty("rejectedDocumentIds")  List<String> rejectedDocumentIds,
            @JsonProperty("rejectionReason")      String rejectionReason) {
        this.decisionId = decisionId;
        this.batchId = batchId; this.decision = decision;
        this.institutionCode = institutionCode; this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.rejectedDocumentIds = rejectedDocumentIds != null ? rejectedDocumentIds : List.of();
        this.rejectionReason = rejectionReason;
    }
}

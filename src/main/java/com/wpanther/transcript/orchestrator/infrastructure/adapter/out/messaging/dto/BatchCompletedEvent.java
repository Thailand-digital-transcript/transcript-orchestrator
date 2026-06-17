package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import java.time.Instant;

public class BatchCompletedEvent extends TraceEvent {

    private static final long serialVersionUID = 1L;

    @JsonProperty("batchId")         private final String batchId;
    @JsonProperty("institutionCode") private final String institutionCode;
    @JsonProperty("itemCount")       private final int itemCount;
    @JsonProperty("completedAt")     private final Instant completedAt;

    public BatchCompletedEvent(String batchId, String institutionCode,
            int itemCount, Instant completedAt) {
        super(batchId, null, "transcript-orchestrator", "BATCH_COMPLETED", null);
        this.batchId = batchId;
        this.institutionCode = institutionCode;
        this.itemCount = itemCount;
        this.completedAt = completedAt;
    }

    public String getBatchId()         { return batchId; }
    public String getInstitutionCode() { return institutionCode; }
    public int getItemCount()          { return itemCount; }
    public Instant getCompletedAt()    { return completedAt; }
}

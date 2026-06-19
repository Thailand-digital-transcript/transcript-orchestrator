package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.transcript.saga.domain.model.TraceEvent;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
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

    @JsonCreator
    public BatchCompletedEvent(
            @JsonProperty("eventId")         UUID eventId,
            @JsonProperty("occurredAt")      Instant occurredAt,
            @JsonProperty("eventType")       String eventType,
            @JsonProperty("version")         Integer version,
            @JsonProperty("sagaId")          String sagaId,
            @JsonProperty("correlationId")   String correlationId,
            @JsonProperty("source")          String source,
            @JsonProperty("traceType")       String traceType,
            @JsonProperty("context")         String context,
            @JsonProperty("batchId")         String batchId,
            @JsonProperty("institutionCode") String institutionCode,
            @JsonProperty("itemCount")       int itemCount,
            @JsonProperty("completedAt")     Instant completedAt) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
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

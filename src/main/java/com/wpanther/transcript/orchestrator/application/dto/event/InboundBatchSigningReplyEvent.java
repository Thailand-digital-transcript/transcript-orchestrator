package com.wpanther.transcript.orchestrator.application.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Inbound worker reply for a batch signing command. Produced by transcript-signing
 * workers for XAdES phases (REGISTRAR / DEAN / SEAL — disambiguated by sagaStep) and
 * the PDF PAdES signing phase. Inbound only — does NOT extend IntegrationEvent.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InboundBatchSigningReplyEvent {
    private final String sagaId;
    private final String sagaStep;
    private final String correlationId;
    private final String status;          // "SUCCESS" | "FAILURE"
    private final String errorMessage;
    private final String batchId;
    private final List<ItemResult> items;

    @JsonCreator
    public InboundBatchSigningReplyEvent(
            @JsonProperty("sagaId")       String sagaId,
            @JsonProperty("sagaStep")     String sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("status")       String status,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("batchId")      String batchId,
            @JsonProperty("items")        List<ItemResult> items) {
        this.sagaId = sagaId;
        this.sagaStep = sagaStep;
        this.correlationId = correlationId;
        this.status = status;
        this.errorMessage = errorMessage;
        this.batchId = batchId;
        this.items = items != null ? items : List.of();
    }

    public String getSagaId()        { return sagaId; }
    public String getSagaStep()      { return sagaStep; }
    public String getCorrelationId() { return correlationId; }
    public String getStatus()        { return status; }
    public String getErrorMessage()  { return errorMessage; }
    public String getBatchId()       { return batchId; }
    public List<ItemResult> getItems() { return items; }

    public boolean isSuccess() { return "SUCCESS".equals(status); }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ItemResult {
        private final String documentId;
        private final String status;       // "SIGNED" | "FAILED"
        private final String signedDocUrl;
        private final Long signedDocSize;
        private final String errorMessage;

        @JsonCreator
        public ItemResult(
                @JsonProperty("documentId")    String documentId,
                @JsonProperty("status")        String status,
                @JsonProperty("signedDocUrl")  String signedDocUrl,
                @JsonProperty("signedDocSize") Long signedDocSize,
                @JsonProperty("errorMessage")  String errorMessage) {
            this.documentId = documentId;
            this.status = status;
            this.signedDocUrl = signedDocUrl;
            this.signedDocSize = signedDocSize;
            this.errorMessage = errorMessage;
        }

        public String getDocumentId()   { return documentId; }
        public String getStatus()       { return status; }
        public String getSignedDocUrl() { return signedDocUrl; }
        public Long getSignedDocSize()  { return signedDocSize; }
        public String getErrorMessage() { return errorMessage; }

        public boolean isSigned() { return "SIGNED".equals(status); }
    }
}

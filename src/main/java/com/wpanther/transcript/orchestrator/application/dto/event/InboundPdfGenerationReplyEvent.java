package com.wpanther.transcript.orchestrator.application.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Inbound worker reply for a PDF generation command. Produced by
 * transcript-pdf-generation workers. Inbound only — does NOT extend
 * IntegrationEvent. Workers may include per-item GENERATED or FAILED
 * results, plus an aggregate status and errorMessage.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InboundPdfGenerationReplyEvent {
    private final String sagaId;
    private final String sagaStep;
    private final String correlationId;
    private final String status;
    private final String errorMessage;
    private final String batchId;
    private final List<ItemResult> items;

    @JsonCreator
    public InboundPdfGenerationReplyEvent(
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
        private final String status;       // "GENERATED" | "FAILED"
        private final String pdfUrl;
        private final Long pdfSize;
        private final String errorMessage;

        @JsonCreator
        public ItemResult(
                @JsonProperty("documentId")  String documentId,
                @JsonProperty("status")      String status,
                @JsonProperty("pdfUrl")      String pdfUrl,
                @JsonProperty("pdfSize")     Long pdfSize,
                @JsonProperty("errorMessage") String errorMessage) {
            this.documentId = documentId;
            this.status = status;
            this.pdfUrl = pdfUrl;
            this.pdfSize = pdfSize;
            this.errorMessage = errorMessage;
        }

        public String getDocumentId()   { return documentId; }
        public String getStatus()       { return status; }
        public String getPdfUrl()       { return pdfUrl; }
        public Long getPdfSize()        { return pdfSize; }
        public String getErrorMessage() { return errorMessage; }

        public boolean isGenerated()    { return "GENERATED".equals(status); }
    }
}

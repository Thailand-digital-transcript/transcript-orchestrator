package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.transcript.saga.domain.enums.SagaStep;
import com.wpanther.transcript.saga.domain.model.SagaCommand;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OutboundPdfGenerationCommand extends SagaCommand {

    private static final long serialVersionUID = 1L;

    @JsonProperty("batchId") private final String batchId;
    @JsonProperty("items")   private final List<Item> items;

    public OutboundPdfGenerationCommand(String sagaId, String correlationId,
            String batchId, List<Item> items) {
        super(sagaId, SagaStep.GENERATE_TRANSCRIPT_PDF, correlationId);
        this.batchId = batchId;
        this.items = items;
    }

    @JsonCreator
    public OutboundPdfGenerationCommand(
            @JsonProperty("eventId")       UUID eventId,
            @JsonProperty("occurredAt")    Instant occurredAt,
            @JsonProperty("eventType")     String eventType,
            @JsonProperty("version")       Integer version,
            @JsonProperty("sagaId")        String sagaId,
            @JsonProperty("sagaStep")      SagaStep sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("batchId")       String batchId,
            @JsonProperty("items")         List<Item> items) {
        super(sagaId, sagaStep, correlationId);
        this.batchId = batchId;
        this.items = items;
    }

    public String getBatchId()     { return batchId; }
    public List<Item> getItems()   { return items; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private final String documentId;
        private final String documentNumber;
        private final String sealedXmlStorageKey;

        @JsonCreator
        public Item(@JsonProperty("documentId") String documentId,
                    @JsonProperty("documentNumber") String documentNumber,
                    @JsonProperty("sealedXmlStorageKey") String sealedXmlStorageKey) {
            this.documentId = documentId;
            this.documentNumber = documentNumber;
            this.sealedXmlStorageKey = sealedXmlStorageKey;
        }

        public String getDocumentId()          { return documentId; }
        public String getDocumentNumber()      { return documentNumber; }
        public String getSealedXmlStorageKey() { return sealedXmlStorageKey; }
    }
}

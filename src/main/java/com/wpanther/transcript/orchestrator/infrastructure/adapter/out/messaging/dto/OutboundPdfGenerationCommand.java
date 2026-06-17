package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;
import java.util.List;

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

    public String getBatchId()     { return batchId; }
    public List<Item> getItems()   { return items; }

    public static class Item {
        @JsonProperty("documentId")          private final String documentId;
        @JsonProperty("documentNumber")      private final String documentNumber;
        @JsonProperty("sealedXmlStorageKey") private final String sealedXmlStorageKey;
        public Item(String documentId, String documentNumber, String sealedXmlStorageKey) {
            this.documentId = documentId; this.documentNumber = documentNumber;
            this.sealedXmlStorageKey = sealedXmlStorageKey;
        }
        public String getDocumentId()          { return documentId; }
        public String getDocumentNumber()      { return documentNumber; }
        public String getSealedXmlStorageKey() { return sealedXmlStorageKey; }
    }
}

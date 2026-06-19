package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.transcript.saga.domain.enums.SagaStep;
import com.wpanther.transcript.saga.domain.model.SagaCommand;
import com.wpanther.transcript.orchestrator.domain.model.SignerRole;
import com.wpanther.transcript.orchestrator.domain.model.SigningFormat;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OutboundBatchSigningCommand extends SagaCommand {

    private static final long serialVersionUID = 1L;

    @JsonProperty("batchId")    private final String batchId;
    @JsonProperty("signerRole") private final SignerRole signerRole;
    @JsonProperty("format")     private final SigningFormat format;
    @JsonProperty("items")      private final List<Item> items;

    public OutboundBatchSigningCommand(String sagaId, String correlationId, String batchId,
            SignerRole signerRole, SigningFormat format, List<Item> items) {
        // Use SIGN_XML for all XAdES phases (REGISTRAR, DEAN, SEAL).
        // signerRole field lets transcript-signing dispatch to the correct signer.
        // Use SIGN_PDF for the final PAdES phase.
        super(sagaId, format == SigningFormat.PDF ? SagaStep.SIGN_PDF : SagaStep.SIGN_XML, correlationId);
        this.batchId = batchId;
        this.signerRole = signerRole;
        this.format = format;
        this.items = items;
    }

    @JsonCreator
    public OutboundBatchSigningCommand(
            @JsonProperty("eventId")       UUID eventId,
            @JsonProperty("occurredAt")    Instant occurredAt,
            @JsonProperty("eventType")     String eventType,
            @JsonProperty("version")       Integer version,
            @JsonProperty("sagaId")        String sagaId,
            @JsonProperty("sagaStep")      SagaStep sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("batchId")       String batchId,
            @JsonProperty("signerRole")    SignerRole signerRole,
            @JsonProperty("format")        SigningFormat format,
            @JsonProperty("items")         List<Item> items) {
        super(sagaId, sagaStep, correlationId);
        this.batchId = batchId;
        this.signerRole = signerRole;
        this.format = format;
        this.items = items;
    }

    public String getBatchId()        { return batchId; }
    public SignerRole getSignerRole() { return signerRole; }
    public SigningFormat getFormat()  { return format; }
    public List<Item> getItems()      { return items; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private final String documentId;
        private final String documentNumber;
        private final String storageKey;

        @JsonCreator
        public Item(@JsonProperty("documentId") String documentId,
                    @JsonProperty("documentNumber") String documentNumber,
                    @JsonProperty("storageKey") String storageKey) {
            this.documentId = documentId;
            this.documentNumber = documentNumber;
            this.storageKey = storageKey;
        }

        public String getDocumentId()     { return documentId; }
        public String getDocumentNumber() { return documentNumber; }
        public String getStorageKey()     { return storageKey; }
    }
}

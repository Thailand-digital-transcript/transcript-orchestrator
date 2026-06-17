package com.wpanther.transcript.orchestrator.application.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InboundStartSagaCommand {
    private final String transcriptId;
    private final String documentId;
    private final String institutionCode;
    private final String transcriptType;
    private final String xmlStorageKey;  // nullable — absent in messages from old processing instances

    @JsonCreator
    public InboundStartSagaCommand(
            @JsonProperty("transcriptId")    String transcriptId,
            @JsonProperty("documentId")      String documentId,
            @JsonProperty("institutionCode") String institutionCode,
            @JsonProperty("transcriptType")  String transcriptType,
            @JsonProperty("xmlStorageKey")   String xmlStorageKey) {
        this.transcriptId = transcriptId; this.documentId = documentId;
        this.institutionCode = institutionCode; this.transcriptType = transcriptType;
        this.xmlStorageKey = xmlStorageKey;
    }
    public String getTranscriptId()    { return transcriptId; }
    public String getDocumentId()      { return documentId; }
    public String getInstitutionCode() { return institutionCode; }
    public String getTranscriptType()  { return transcriptType; }
    public String getXmlStorageKey()   { return xmlStorageKey; }
}

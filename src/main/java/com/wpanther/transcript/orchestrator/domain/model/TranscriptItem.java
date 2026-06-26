package com.wpanther.transcript.orchestrator.domain.model;

import java.time.Instant;
import java.util.UUID;

public class TranscriptItem {

    private UUID id;
    private String transcriptId;
    private String documentId;
    private String institutionCode;
    private String transcriptType;
    private String originalXmlStorageKey;
    private ItemStatus status;
    private UUID batchId;
    private String registrarSignedXmlKey;
    private String deanSignedXmlKey;
    private String sealedXmlKey;
    private String pdfKey;
    private String signedPdfKey;
    private String rejectionReason;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    private TranscriptItem() {}

    public static TranscriptItem register(String transcriptId, String documentId,
            String institutionCode, String transcriptType, String originalXmlStorageKey) {
        TranscriptItem i = new TranscriptItem();
        i.id = UUID.randomUUID();
        i.transcriptId = transcriptId;
        i.documentId = documentId;
        i.institutionCode = institutionCode;
        i.transcriptType = transcriptType;
        i.originalXmlStorageKey = originalXmlStorageKey;
        i.status = ItemStatus.REGISTERED;
        i.createdAt = Instant.now();
        i.updatedAt = i.createdAt;
        return i;
    }

    /**
     * Assigns item to a batch. Requires a non-null originalXmlStorageKey
     * (G1 Rev 1 fix: guards against null-keyed items that would later NPE
     * in currentSigningStorageKey()).
     */
    public void assign(UUID batchId) {
        if (this.originalXmlStorageKey == null) {
            throw new IllegalStateException(
                "Cannot assign item " + transcriptId + ": xmlStorageKey is null (XML not yet uploaded)");
        }
        this.batchId = batchId;
        this.status = ItemStatus.ASSIGNED;
        this.updatedAt = Instant.now();
    }

    public void unassign() {
        this.batchId = null;
        this.status = ItemStatus.REGISTERED;
        this.updatedAt = Instant.now();
    }

    public void markRegistrarSigned(String key) {
        this.registrarSignedXmlKey = key; this.status = ItemStatus.REGISTRAR_SIGNED;
        this.updatedAt = Instant.now();
    }
    public void markDeanSigned(String key) {
        this.deanSignedXmlKey = key; this.status = ItemStatus.DEAN_SIGNED;
        this.updatedAt = Instant.now();
    }
    public void markSealed(String key) {
        this.sealedXmlKey = key; this.status = ItemStatus.SEALED;
        this.updatedAt = Instant.now();
    }
    public void markPdfRendered(String key) {
        this.pdfKey = key; this.status = ItemStatus.PDF_RENDERED;
        this.updatedAt = Instant.now();
    }
    public void markPdfSigned(String key) {
        this.signedPdfKey = key; this.status = ItemStatus.PDF_SIGNED;
        this.updatedAt = Instant.now();
    }
    public void reject(String reason) {
        this.rejectionReason = reason; this.status = ItemStatus.REJECTED;
        this.updatedAt = Instant.now();
    }
    public void fail(String reason) {
        this.failureReason = reason; this.status = ItemStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    /**
     * Returns the storage key to feed the next signing step. Through the XAdES phases
     * this is the most recently produced XML key; once the PDF has been rendered the
     * next phase is PAdES, which must sign the rendered PDF rather than the sealed XML.
     */
    public String currentSigningStorageKey() {
        if (status == ItemStatus.PDF_RENDERED && pdfKey != null) return pdfKey;
        if (sealedXmlKey != null)          return sealedXmlKey;
        if (deanSignedXmlKey != null)      return deanSignedXmlKey;
        if (registrarSignedXmlKey != null) return registrarSignedXmlKey;
        return originalXmlStorageKey;
    }

    public boolean isTerminal() { return status.isTerminal(); }
    public boolean isHealthy()  { return !isTerminal(); }

    public UUID getId()                      { return id; }
    public String getTranscriptId()          { return transcriptId; }
    public String getDocumentId()            { return documentId; }
    public String getInstitutionCode()       { return institutionCode; }
    public String getTranscriptType()        { return transcriptType; }
    public String getOriginalXmlStorageKey() { return originalXmlStorageKey; }
    public ItemStatus getStatus()            { return status; }
    public UUID getBatchId()                 { return batchId; }
    public String getRegistrarSignedXmlKey() { return registrarSignedXmlKey; }
    public String getDeanSignedXmlKey()      { return deanSignedXmlKey; }
    public String getSealedXmlKey()          { return sealedXmlKey; }
    public String getPdfKey()                { return pdfKey; }
    public String getSignedPdfKey()          { return signedPdfKey; }
    public String getRejectionReason()       { return rejectionReason; }
    public String getFailureReason()         { return failureReason; }
    public Instant getCreatedAt()            { return createdAt; }
    public Instant getUpdatedAt()            { return updatedAt; }
}

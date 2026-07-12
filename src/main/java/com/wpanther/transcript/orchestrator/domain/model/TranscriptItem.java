package com.wpanther.transcript.orchestrator.domain.model;

import com.wpanther.transcript.orchestrator.domain.service.TranscriptKeyResolver;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    private Instant artifactsPurgedAt;

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
     * (G1 Rev 1 fix: guards against null-keyed items, which would otherwise cause
     * {@link #latestXmlRef} / {@link #nextSigningSource} to throw
     * {@code IllegalArgumentException} when the resolver rejects the null key).
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
     * What the next signing round reads. Walks original → registrar → dean → sealed →
     * rendered PDF. The PAdES round signs the PDF, not the sealed XML.
     */
    public StorageRef nextSigningSource(TranscriptKeyResolver resolver) {
        if (status == ItemStatus.PDF_RENDERED && pdfKey != null) return resolver.renderedPdf(originalXmlStorageKey);
        if (sealedXmlKey != null)          return resolver.sealed(originalXmlStorageKey);
        if (deanSignedXmlKey != null)      return resolver.deanSigned(originalXmlStorageKey);
        if (registrarSignedXmlKey != null) return resolver.registrarSigned(originalXmlStorageKey);
        return resolver.original(originalXmlStorageKey);
    }

    /**
     * What the UI displays. Identical to {@link #nextSigningSource} except at
     * PDF_RENDERED, where the two answers diverge — and where the old single method
     * handed the viewer a PDF key to presign against the XML bucket. Never returns the PDF.
     */
    public StorageRef latestXmlRef(TranscriptKeyResolver resolver) {
        if (sealedXmlKey != null)          return resolver.sealed(originalXmlStorageKey);
        if (deanSignedXmlKey != null)      return resolver.deanSigned(originalXmlStorageKey);
        if (registrarSignedXmlKey != null) return resolver.registrarSigned(originalXmlStorageKey);
        return resolver.original(originalXmlStorageKey);
    }

    public boolean isTerminal() { return status.isTerminal(); }
    public boolean isHealthy()  { return !isTerminal(); }

    /**
     * Intermediate artifacts that become garbage once this item dies. The saga is
     * forward-only — a failed item is marked FAILED and the healthy ones carry on — so
     * nothing else ever reclaims them.
     *
     * <p>Now spans two buckets: the signed XML/PDF artifacts in signed-transcripts, and the
     * rendered PDF in transcript-pdfs. pdfKey was previously excluded ONLY because it held a
     * presigned URL rather than a key; that is fixed, so it is reclaimed like the rest. The
     * startsWith("http") guard that worked around it is gone with the bug.
     *
     * <p>originalXmlStorageKey remains excluded: it is processing's source of truth and the
     * only copy of the submitted transcript. Never ours to delete.
     */
    public List<StorageRef> purgeableArtifactRefs(TranscriptKeyResolver resolver) {
        List<StorageRef> refs = new ArrayList<>();
        if (registrarSignedXmlKey != null) refs.add(resolver.registrarSigned(originalXmlStorageKey));
        if (deanSignedXmlKey != null)      refs.add(resolver.deanSigned(originalXmlStorageKey));
        if (sealedXmlKey != null)          refs.add(resolver.sealed(originalXmlStorageKey));
        if (pdfKey != null)                refs.add(resolver.renderedPdf(originalXmlStorageKey));
        if (signedPdfKey != null)          refs.add(resolver.sealedPdf(originalXmlStorageKey));
        return refs;
    }

    /** Marks the artifacts reclaimed so the sweeper does not revisit this item. */
    public void markArtifactsPurged() {
        this.artifactsPurgedAt = Instant.now();
        this.updatedAt = this.artifactsPurgedAt;
    }

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
    public Instant getArtifactsPurgedAt()    { return artifactsPurgedAt; }
}

package com.wpanther.transcript.orchestrator.domain.model;

import java.time.Instant;
import java.util.UUID;

public class Batch {

    private UUID id;
    private String name;
    private String institutionCode;
    private BatchStatus status;
    private String awaitingReplyFor;
    private int itemCount;
    private String createdBy;
    private String closedBy;
    private Instant closedAt;
    private String registrarApprovedBy;
    private Instant registrarApprovedAt;
    private String deanApprovedBy;
    private Instant deanApprovedAt;
    private String rejectedBy;
    private Instant rejectedAt;
    private String rejectionReason;
    private String failureReason;
    private Instant createdAt;
    private Instant completedAt;

    // Set only by BatchEntityMapper (same package) for optimistic locking
    Long version;

    private Batch() {}

    public static Batch create(String name, String institutionCode, String createdBy) {
        Batch b = new Batch();
        b.id = UUID.randomUUID();
        b.name = name;
        b.institutionCode = institutionCode;
        b.createdBy = createdBy;
        b.status = BatchStatus.DRAFT;
        b.itemCount = 0;
        b.createdAt = Instant.now();
        return b;
    }

    // --- state transition methods (called by BatchStateMachine after validation) ---

    public void applyClose(String closedBy, Instant closedAt) {
        this.status = BatchStatus.PENDING_REGISTRAR;
        this.closedBy = closedBy;
        this.closedAt = closedAt;
    }
    public void applyRegistrarApproved(String by, Instant at) {
        this.status = BatchStatus.REGISTRAR_SIGNING;
        this.registrarApprovedBy = by;
        this.registrarApprovedAt = at;
    }
    public void applyDeanApproved(String by, Instant at) {
        this.status = BatchStatus.DEAN_SIGNING;
        this.deanApprovedBy = by;
        this.deanApprovedAt = at;
    }
    public void applyCancelled(String by, Instant at, String reason) {
        this.status = BatchStatus.CANCELLED;
        this.rejectedBy = by;
        this.rejectedAt = at;
        this.rejectionReason = reason;
    }
    public void applyFailed(String reason) {
        this.status = BatchStatus.FAILED;
        this.failureReason = reason;
    }
    public void applySigningStarted(String correlationId) {
        this.awaitingReplyFor = correlationId;
    }
    public void applyPdfGenerationStarted(String correlationId) {
        this.awaitingReplyFor = correlationId;
    }
    public void applyRegistrarSigningComplete() {
        this.status = BatchStatus.PENDING_DEAN;
        this.awaitingReplyFor = null;
    }
    public void applyDeanSigningComplete() {
        this.status = BatchStatus.SEALING;
        this.awaitingReplyFor = null;
    }
    public void applySealingComplete() {
        this.status = BatchStatus.PDF_GENERATION;
        this.awaitingReplyFor = null;
    }
    public void applyPdfGenerationComplete() {
        this.status = BatchStatus.PDF_SIGNING;
        this.awaitingReplyFor = null;
    }
    public void applyPdfSigningComplete() {
        this.status = BatchStatus.COMPLETED;
        this.awaitingReplyFor = null;
        this.completedAt = Instant.now();
    }

    // --- item count (called by use cases within same TX) ---
    public void incrementItemCount() { this.itemCount++; }
    public void decrementItemCount() { if (this.itemCount > 0) this.itemCount--; }

    // --- package-private setters for JPA restore (BatchEntityMapper / BatchEntity.toDomain) ---
    void setItemCount(int n)           { this.itemCount = n; }
    void setStatus(BatchStatus s)      { this.status = s; }
    void setAwaitingReplyFor(String c) { this.awaitingReplyFor = c; }
    void setVersion(Long version)     { this.version = version; }

    // --- getters ---
    public UUID getId()                     { return id; }
    public String getName()                 { return name; }
    public String getInstitutionCode()      { return institutionCode; }
    public BatchStatus getStatus()          { return status; }
    public String getAwaitingReplyFor()     { return awaitingReplyFor; }
    public Long getVersion()                { return version; }
    public int getItemCount()               { return itemCount; }
    public String getCreatedBy()            { return createdBy; }
    public String getClosedBy()             { return closedBy; }
    public Instant getClosedAt()            { return closedAt; }
    public String getRegistrarApprovedBy()  { return registrarApprovedBy; }
    public Instant getRegistrarApprovedAt() { return registrarApprovedAt; }
    public String getDeanApprovedBy()       { return deanApprovedBy; }
    public Instant getDeanApprovedAt()      { return deanApprovedAt; }
    public String getRejectedBy()           { return rejectedBy; }
    public Instant getRejectedAt()          { return rejectedAt; }
    public String getRejectionReason()      { return rejectionReason; }
    public String getFailureReason()        { return failureReason; }
    public Instant getCreatedAt()           { return createdAt; }
    public Instant getCompletedAt()         { return completedAt; }
}

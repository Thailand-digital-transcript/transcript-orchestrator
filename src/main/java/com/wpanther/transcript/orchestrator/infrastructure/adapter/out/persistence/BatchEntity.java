package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.persistence;

import com.wpanther.transcript.orchestrator.domain.model.Batch;
import com.wpanther.transcript.orchestrator.domain.model.BatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "batches")
@Getter
@Setter
public class BatchEntity {

    @Id
    private UUID id;

    private String name;
    private String institutionCode;

    @Enumerated(EnumType.STRING)
    private BatchStatus status;

    private String awaitingReplyFor;

    @Version
    private Long version;

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

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant completedAt;

    /**
     * M4 fix: keep updated_at in sync with each persist/update so the
     * stuck-phase sweeper query can find batches that haven't progressed.
     */
    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public static BatchEntity fromDomain(Batch b) {
        BatchEntity e = new BatchEntity();
        e.id = b.getId();
        e.name = b.getName();
        e.institutionCode = b.getInstitutionCode();
        e.status = b.getStatus();
        e.awaitingReplyFor = b.getAwaitingReplyFor();
        e.version = b.getVersion();
        e.itemCount = b.getItemCount();
        e.createdBy = b.getCreatedBy();
        e.closedBy = b.getClosedBy();
        e.closedAt = b.getClosedAt();
        e.registrarApprovedBy = b.getRegistrarApprovedBy();
        e.registrarApprovedAt = b.getRegistrarApprovedAt();
        e.deanApprovedBy = b.getDeanApprovedBy();
        e.deanApprovedAt = b.getDeanApprovedAt();
        e.rejectedBy = b.getRejectedBy();
        e.rejectedAt = b.getRejectedAt();
        e.rejectionReason = b.getRejectionReason();
        e.failureReason = b.getFailureReason();
        e.createdAt = b.getCreatedAt();
        e.completedAt = b.getCompletedAt();
        return e;
    }

    public Batch toDomain() {
        Batch b = Batch.create(name, institutionCode, createdBy);
        BatchEntityMapper.restore(b, this);
        return b;
    }
}

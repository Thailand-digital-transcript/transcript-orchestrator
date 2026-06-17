package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.persistence;

import com.wpanther.transcript.orchestrator.domain.model.ItemStatus;
import com.wpanther.transcript.orchestrator.domain.model.TranscriptItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transcript_items")
@Getter
@Setter
public class TranscriptItemEntity {

    @Id
    private UUID id;

    private String transcriptId;
    private String documentId;
    private String institutionCode;
    private String transcriptType;
    private String originalXmlStorageKey;

    @Enumerated(EnumType.STRING)
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

    @Column(nullable = false)
    private Instant updatedAt;

    public static TranscriptItemEntity fromDomain(TranscriptItem i) {
        TranscriptItemEntity e = new TranscriptItemEntity();
        e.id = i.getId();
        e.transcriptId = i.getTranscriptId();
        e.documentId = i.getDocumentId();
        e.institutionCode = i.getInstitutionCode();
        e.transcriptType = i.getTranscriptType();
        e.originalXmlStorageKey = i.getOriginalXmlStorageKey();
        e.status = i.getStatus();
        e.batchId = i.getBatchId();
        e.registrarSignedXmlKey = i.getRegistrarSignedXmlKey();
        e.deanSignedXmlKey = i.getDeanSignedXmlKey();
        e.sealedXmlKey = i.getSealedXmlKey();
        e.pdfKey = i.getPdfKey();
        e.signedPdfKey = i.getSignedPdfKey();
        e.rejectionReason = i.getRejectionReason();
        e.failureReason = i.getFailureReason();
        e.createdAt = i.getCreatedAt();
        e.updatedAt = i.getUpdatedAt();
        return e;
    }

    public TranscriptItem toDomain() {
        TranscriptItem i = TranscriptItem.register(
                transcriptId, documentId, institutionCode,
                transcriptType, originalXmlStorageKey);
        TranscriptItemEntityMapper.restore(i, this);
        return i;
    }
}

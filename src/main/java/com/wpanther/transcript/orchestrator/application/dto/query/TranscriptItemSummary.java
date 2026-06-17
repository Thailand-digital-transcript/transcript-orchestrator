package com.wpanther.transcript.orchestrator.application.dto.query;

import com.wpanther.transcript.orchestrator.domain.model.*;
import java.util.UUID;
public record TranscriptItemSummary(
    UUID id, String transcriptId, String documentId, String institutionCode,
    String transcriptType, ItemStatus status, UUID batchId
) {
    public static TranscriptItemSummary from(TranscriptItem i) {
        return new TranscriptItemSummary(i.getId(), i.getTranscriptId(), i.getDocumentId(),
            i.getInstitutionCode(), i.getTranscriptType(), i.getStatus(), i.getBatchId());
    }
}

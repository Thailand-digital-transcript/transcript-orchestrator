package com.wpanther.transcript.orchestrator.application.dto.query;

import com.wpanther.transcript.orchestrator.domain.model.*;
import java.time.Instant; import java.util.List; import java.util.UUID;
public record BatchDetail(
    UUID id, String name, String institutionCode, BatchStatus status,
    int itemCount, String createdBy, Instant createdAt, Instant completedAt,
    String registrarApprovedBy, Instant registrarApprovedAt,
    String deanApprovedBy, Instant deanApprovedAt,
    String rejectionReason, String failureReason,
    List<TranscriptItemSummary> items
) {
    public static BatchDetail from(Batch b, List<TranscriptItemSummary> items) {
        return new BatchDetail(b.getId(), b.getName(), b.getInstitutionCode(), b.getStatus(),
            b.getItemCount(), b.getCreatedBy(), b.getCreatedAt(), b.getCompletedAt(),
            b.getRegistrarApprovedBy(), b.getRegistrarApprovedAt(),
            b.getDeanApprovedBy(), b.getDeanApprovedAt(),
            b.getRejectionReason(), b.getFailureReason(), items);
    }
}

package com.wpanther.transcript.orchestrator.application.dto.query;

import com.wpanther.transcript.orchestrator.domain.model.*;
import java.time.Instant; import java.util.UUID;
public record BatchSummary(
    UUID id, String name, String institutionCode, BatchStatus status,
    int itemCount, String createdBy, Instant createdAt
) {
    public static BatchSummary from(Batch b) {
        return new BatchSummary(b.getId(), b.getName(), b.getInstitutionCode(),
            b.getStatus(), b.getItemCount(), b.getCreatedBy(), b.getCreatedAt());
    }
}

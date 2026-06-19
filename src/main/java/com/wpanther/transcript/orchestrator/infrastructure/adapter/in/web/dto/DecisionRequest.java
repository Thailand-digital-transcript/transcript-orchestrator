package com.wpanther.transcript.orchestrator.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Request body for {@code POST /api/v1/batches/{id}/decision} (A10).
 *
 * <p>Validation of the {@code decision}/{@code rejectionReason}/{@code rejectedDocumentIds}
 * semantics is delegated to {@code SubmitBatchDecisionUseCase} (which throws
 * {@code DecisionValidationException} → 400). The controller intentionally
 * performs NO bean-validation here so all decision-shape errors share one
 * mapped error response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DecisionRequest(
        String decision,
        List<String> rejectedDocumentIds,
        String rejectionReason) {
}

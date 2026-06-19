package com.wpanther.transcript.orchestrator.domain.repository;

import com.wpanther.transcript.orchestrator.domain.model.Batch;
import com.wpanther.transcript.orchestrator.domain.model.BatchStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchRepository {
    Batch save(Batch batch);
    Optional<Batch> findById(UUID id);
    Optional<Batch> findByAwaitingReplyFor(String correlationId);
    List<Batch> findByStatusIn(List<BatchStatus> statuses, int limit);
    List<Batch> findAutomaticPhaseStuckBefore(Instant before, int limit);

    /**
     * Approval-queue read for a single institution. Used by the A10 controller's
     * JWT-scoped queue endpoint. {@code limit} bounds the page size (page 0).
     */
    List<Batch> findByStatusInAndInstitutionCode(List<BatchStatus> statuses,
                                                 String institutionCode,
                                                 int limit);

    /**
     * Paginated monitor read for a single institution.
     */
    List<Batch> findByInstitutionCode(String institutionCode, int page, int size);

    /**
     * Total batch count for a single institution (drives the paginated monitor's
     * {@code totalElements}).
     */
    long countByInstitutionCode(String institutionCode);
}

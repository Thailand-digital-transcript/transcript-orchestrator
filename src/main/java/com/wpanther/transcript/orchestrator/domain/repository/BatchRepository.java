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
}

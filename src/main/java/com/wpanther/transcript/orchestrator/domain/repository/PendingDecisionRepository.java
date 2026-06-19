package com.wpanther.transcript.orchestrator.domain.repository;

import com.wpanther.transcript.orchestrator.domain.model.PendingDecision;
import java.util.UUID;

public interface PendingDecisionRepository {
    PendingDecision save(PendingDecision decision);

    /**
     * Atomically claim a decision for processing. Returns true iff this caller
     * set processed_at (rowCount == 1); false if already claimed (replay/race).
     */
    boolean claim(UUID decisionId);
}

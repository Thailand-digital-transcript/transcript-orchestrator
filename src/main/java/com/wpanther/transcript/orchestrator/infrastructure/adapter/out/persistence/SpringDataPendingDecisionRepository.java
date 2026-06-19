package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface SpringDataPendingDecisionRepository extends JpaRepository<PendingDecisionEntity, UUID> {

    // Spring's @Transactional (not jakarta's) to match the rest of the orchestrator
    // and guarantee the conditional UPDATE runs in a managed TX, not auto-commit —
    // the single-winner dedupe guarantee (spec §4.5) depends on it.
    @Modifying @Transactional
    @Query(value = "UPDATE pending_decision SET processed_at = now() "
            + "WHERE decision_id = :id AND processed_at IS NULL", nativeQuery = true)
    int claim(@Param("id") UUID decisionId);
}

package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.persistence;

import com.wpanther.transcript.orchestrator.domain.model.PendingDecision;
import com.wpanther.transcript.orchestrator.domain.repository.PendingDecisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PendingDecisionRepositoryAdapter implements PendingDecisionRepository {
    private final SpringDataPendingDecisionRepository jpa;

    @Override
    public PendingDecision save(PendingDecision decision) {
        return jpa.save(PendingDecisionEntity.fromDomain(decision)).toDomain();
    }

    @Override
    public boolean claim(UUID decisionId) {
        return jpa.claim(decisionId) == 1;
    }
}

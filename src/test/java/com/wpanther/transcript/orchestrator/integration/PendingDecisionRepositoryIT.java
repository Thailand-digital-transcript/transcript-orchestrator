package com.wpanther.transcript.orchestrator.integration;

import com.wpanther.transcript.orchestrator.domain.model.BatchStatus;
import com.wpanther.transcript.orchestrator.domain.model.PendingDecision;
import com.wpanther.transcript.orchestrator.domain.repository.PendingDecisionRepository;
import com.wpanther.transcript.orchestrator.integration.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PendingDecisionRepositoryIT extends IntegrationTestBase {

    @Autowired PendingDecisionRepository repo;

    @Test
    void claimIsAtomicAndSingleWinner() {
        UUID id = UUID.randomUUID();
        repo.save(new PendingDecision(id, UUID.randomUUID(), BatchStatus.PENDING_REGISTRAR,
                "APPROVE", "alice", "01110", null, List.of(), null));

        assertThat(repo.claim(id)).isTrue();   // first wins
        assertThat(repo.claim(id)).isFalse();  // replay loses
    }
}

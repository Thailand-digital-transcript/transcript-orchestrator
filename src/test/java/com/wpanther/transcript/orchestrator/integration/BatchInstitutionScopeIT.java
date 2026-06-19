package com.wpanther.transcript.orchestrator.integration;

import com.wpanther.transcript.orchestrator.domain.model.Batch;
import com.wpanther.transcript.orchestrator.domain.model.BatchStatus;
import com.wpanther.transcript.orchestrator.domain.repository.BatchRepository;
import com.wpanther.transcript.orchestrator.integration.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the A8 institution-scoped repository reads:
 * {@code findByStatusInAndInstitutionCode}, {@code findByInstitutionCode},
 * and {@code countByInstitutionCode}.
 *
 * <p>Seeds two institutions (mirroring the institution-isolation pattern in
 * {@code OrchestratorInstitutionIsolationIT} but directly via {@link BatchRepository#save},
 * which is the unit under test) and asserts that scoping by institution returns
 * only the matching batches, that pagination respects page/size, and that the
 * count matches the seeded rows for the institution.
 */
class BatchInstitutionScopeIT extends IntegrationTestBase {

    @Autowired BatchRepository repo;

    @Test
    void scopesByInstitution() {
        // IntegrationTestBase shares ONE Postgres across the whole IT suite, so
        // other ITs (DecisionEndpointIT, OrchestratorHappyPathIT, ...) leave batch
        // rows behind. Use per-run UNIQUE institution codes (VARCHAR(50) — a 42-char
        // "SCOPE-<uuid>" fits) so the exact-match / count assertions below see only
        // this test's seeded rows. instEmpty is never seeded → its count must be 0.
        String instMine = "SCOPE-" + UUID.randomUUID();
        String instOther = "SCOPE-" + UUID.randomUUID();
        String instEmpty = "SCOPE-" + UUID.randomUUID();

        // Seed two PENDING_REGISTRAR batches in instMine and one in instOther.
        Batch mine1 = repo.save(newPendingBatch("scope-a", instMine, "alice"));
        Batch mine2 = repo.save(newPendingBatch("scope-b", instMine, "alice"));
        Batch other = repo.save(newPendingBatch("scope-c", instOther, "bob"));

        // findByStatusInAndInstitutionCode: queue read for one institution.
        List<Batch> queue = repo.findByStatusInAndInstitutionCode(
                List.of(BatchStatus.PENDING_REGISTRAR), instMine, 100);
        assertThat(queue)
                .extracting(Batch::getId)
                .containsExactlyInAnyOrder(mine1.getId(), mine2.getId())
                .doesNotContain(other.getId());
        assertThat(queue).allMatch(b -> instMine.equals(b.getInstitutionCode()));
        assertThat(queue).allMatch(b -> b.getStatus() == BatchStatus.PENDING_REGISTRAR);

        // findByInstitutionCode: paginated monitor read.
        List<Batch> page0 = repo.findByInstitutionCode(instMine, 0, 10);
        assertThat(page0)
                .extracting(Batch::getId)
                .containsExactlyInAnyOrder(mine1.getId(), mine2.getId());
        assertThat(page0).allMatch(b -> instMine.equals(b.getInstitutionCode()));

        // Pagination: page size 1 splits the two rows.
        List<Batch> firstOnly = repo.findByInstitutionCode(instMine, 0, 1);
        List<Batch> secondOnly = repo.findByInstitutionCode(instMine, 1, 1);
        assertThat(firstOnly).hasSize(1);
        assertThat(secondOnly).hasSize(1);
        assertThat(firstOnly.get(0).getId()).isNotEqualTo(secondOnly.get(0).getId());

        // countByInstitutionCode: total elements for the monitor.
        assertThat(repo.countByInstitutionCode(instMine)).isEqualTo(2L);
        assertThat(repo.countByInstitutionCode(instOther)).isEqualTo(1L);
        assertThat(repo.countByInstitutionCode(instEmpty)).isZero();
    }

    /**
     * Builds a {@code PENDING_REGISTRAR} batch directly via the domain factory
     * and {@code applyClose} (no state machine needed; this IT exercises the
     * repository reads, not the transition validation). Mirrors the direct-save
     * seed pattern in {@code PendingDecisionRepositoryIT}.
     */
    private static Batch newPendingBatch(String name, String institutionCode, String createdBy) {
        Batch b = Batch.create(name, institutionCode, createdBy);
        b.applyClose("seeder", Instant.now());
        return b;
    }
}

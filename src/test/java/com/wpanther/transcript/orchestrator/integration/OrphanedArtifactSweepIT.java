package com.wpanther.transcript.orchestrator.integration;

import com.wpanther.transcript.orchestrator.domain.model.TranscriptItem;
import com.wpanther.transcript.orchestrator.domain.repository.TranscriptItemRepository;
import com.wpanther.transcript.orchestrator.integration.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the V3 migration and the sweep query against real Postgres. The unit tests mock
 * the repository, so nothing else proves that {@code artifacts_purged_at} actually exists,
 * that Hibernate's {@code ddl-auto=validate} accepts the mapped column, or that the JPQL
 * status/NULL filter selects what we think it does.
 */
class OrphanedArtifactSweepIT extends IntegrationTestBase {

    @Autowired TranscriptItemRepository items;

    /**
     * Left unassigned on purpose: batch_id carries a foreign key, and the batch an item
     * belonged to is irrelevant to the sweep query, which filters on status and
     * artifacts_purged_at alone.
     */
    private TranscriptItem persistedItem(String txId) {
        TranscriptItem i = TranscriptItem.register(txId, "doc-" + txId, "KMUTT", "REGULAR",
                "original/" + txId + ".xml");
        i.markRegistrarSigned(txId + "/registrar.xml");
        return i;
    }

    @Test
    void sweepQuery_findsOnlyTerminalUnpurgedItems() {
        TranscriptItem dead = persistedItem("tx-dead-" + UUID.randomUUID());
        dead.fail("sealing failed");
        items.save(dead);

        TranscriptItem healthy = persistedItem("tx-live-" + UUID.randomUUID());
        items.save(healthy);

        TranscriptItem alreadySwept = persistedItem("tx-swept-" + UUID.randomUUID());
        alreadySwept.fail("sealing failed");
        alreadySwept.markArtifactsPurged();
        items.save(alreadySwept);

        // Cutoff in the future so the freshly-written rows are all "old enough".
        List<String> found = items
                .findTerminalWithUnpurgedArtifacts(Instant.now().plusSeconds(60), 100)
                .stream().map(TranscriptItem::getTranscriptId).toList();

        assertThat(found)
                .contains(dead.getTranscriptId())
                .doesNotContain(healthy.getTranscriptId())      // still in flight
                .doesNotContain(alreadySwept.getTranscriptId()); // idempotence: swept once only
    }

    @Test
    void sweepQuery_respectsTheRetentionCutoff() {
        TranscriptItem justDied = persistedItem("tx-fresh-" + UUID.randomUUID());
        justDied.fail("sealing failed");
        items.save(justDied);

        // A cutoff in the past excludes an item that died a moment ago — the grace period
        // that leaves an operator time to inspect a fresh failure.
        List<String> found = items
                .findTerminalWithUnpurgedArtifacts(Instant.now().minusSeconds(3600), 100)
                .stream().map(TranscriptItem::getTranscriptId).toList();

        assertThat(found).doesNotContain(justDied.getTranscriptId());
    }

    @Test
    void purgedTimestamp_survivesTheRoundTrip() {
        TranscriptItem item = persistedItem("tx-rt-" + UUID.randomUUID());
        item.fail("sealing failed");
        item.markArtifactsPurged();
        items.save(item);

        TranscriptItem reloaded = items.findByTranscriptId(item.getTranscriptId()).orElseThrow();
        assertThat(reloaded.getArtifactsPurgedAt()).isNotNull();
        // The keys must survive too — the sweeper reads them back off a reloaded row.
        assertThat(reloaded.purgeableArtifactKeys())
                .containsExactly(item.getTranscriptId() + "/registrar.xml");
    }
}

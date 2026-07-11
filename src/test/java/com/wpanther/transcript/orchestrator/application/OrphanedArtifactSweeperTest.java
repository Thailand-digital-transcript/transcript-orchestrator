package com.wpanther.transcript.orchestrator.application;

import com.wpanther.transcript.orchestrator.application.port.out.ArtifactStoragePort;
import com.wpanther.transcript.orchestrator.application.usecase.OrphanedArtifactPurgeTask;
import com.wpanther.transcript.orchestrator.application.usecase.OrphanedArtifactSweeper;
import com.wpanther.transcript.orchestrator.domain.model.TranscriptItem;
import com.wpanther.transcript.orchestrator.domain.repository.TranscriptItemRepository;
import com.wpanther.transcript.orchestrator.infrastructure.config.OrchestratorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrphanedArtifactSweeperTest {

    TranscriptItemRepository repository = mock(TranscriptItemRepository.class);
    ArtifactStoragePort storage = mock(ArtifactStoragePort.class);
    OrchestratorProperties props = new OrchestratorProperties();

    OrphanedArtifactPurgeTask purgeTask;
    OrphanedArtifactSweeper sweeper;

    @BeforeEach
    void setUp() {
        purgeTask = new OrphanedArtifactPurgeTask(repository, storage);
        sweeper = new OrphanedArtifactSweeper(repository, purgeTask, props);
    }

    private TranscriptItem deadItemSignedThroughDean() {
        return deadItemSignedThroughDean("tx-1");
    }

    private TranscriptItem deadItemSignedThroughDean(String txId) {
        TranscriptItem i = TranscriptItem.register(txId, "doc-1", "KMUTT", "REGULAR",
                "2026/07/10/01/original.xml");
        i.assign(UUID.randomUUID());
        i.markRegistrarSigned(txId + "/registrar.xml");
        i.markDeanSigned(txId + "/dean.xml");
        i.fail("sealing failed");
        return i;
    }

    @Test
    void purge_deletesIntermediates_butNeverTheOriginal() {
        TranscriptItem item = deadItemSignedThroughDean();

        purgeTask.purge(item);

        verify(storage).delete("tx-1/registrar.xml");
        verify(storage).delete("tx-1/dean.xml");
        // The submitted transcript is processing's source of truth. Deleting it would
        // destroy the only copy — this is the assertion that must never go green wrongly.
        verify(storage, never()).delete("2026/07/10/01/original.xml");
        assertThat(item.getArtifactsPurgedAt()).isNotNull();
        verify(repository).save(item);
    }

    @Test
    void purge_marksItemOnlyAfterDeletesSucceed() {
        TranscriptItem item = deadItemSignedThroughDean();
        doThrow(new RuntimeException("S3 down")).when(storage).delete("tx-1/registrar.xml");

        try {
            purgeTask.purge(item);
        } catch (RuntimeException expected) {
            // swallowed by the sweeper in production
        }

        // artifacts_purged_at must stay NULL so the next sweep retries. Marking it purged
        // here would strand the objects: no later sweep would ever look at this item again.
        assertThat(item.getArtifactsPurgedAt()).isNull();
        verify(repository, never()).save(item);
    }

    @Test
    void sweep_isolatesFailures_andContinuesWithRemainingItems() {
        TranscriptItem bad = deadItemSignedThroughDean("tx-bad");
        TranscriptItem good = deadItemSignedThroughDean("tx-good");
        when(repository.findTerminalWithUnpurgedArtifacts(any(Instant.class), anyInt()))
                .thenReturn(List.of(bad, good));
        doThrow(new RuntimeException("S3 down")).when(storage).delete("tx-bad/registrar.xml");

        sweeper.sweep();

        // One item exploding must not abandon the rest of the sweep.
        verify(repository, never()).save(bad);
        verify(repository).save(good);
    }

    @Test
    void sweep_appliesTheRetentionGracePeriod() {
        when(repository.findTerminalWithUnpurgedArtifacts(any(Instant.class), anyInt()))
                .thenReturn(List.of());
        props.setOrphanRetentionHours(168);

        Instant before = Instant.now();
        sweeper.sweep();

        var cutoff = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(repository).findTerminalWithUnpurgedArtifacts(cutoff.capture(), eq(100));
        // Items that died inside the grace window are left alone for post-mortem inspection.
        assertThat(cutoff.getValue()).isBefore(before.minusSeconds(167 * 3600));
        verifyNoInteractions(storage);
    }
}

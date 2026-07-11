package com.wpanther.transcript.orchestrator.application.usecase;

import com.wpanther.transcript.orchestrator.domain.model.TranscriptItem;
import com.wpanther.transcript.orchestrator.domain.repository.TranscriptItemRepository;
import com.wpanther.transcript.orchestrator.infrastructure.config.OrchestratorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Reclaims the intermediate signing artifacts of items that died partway through the saga.
 *
 * <p>The saga is deliberately forward-only: a failed item is marked FAILED, the healthy items
 * carry on, and the batch fails only if every item does. There are no compensating
 * transactions anywhere — the orchestrator never emits one. That is a reasonable design for
 * append-only signing, but it leaves a gap: an item that fails at the dean phase has already
 * written a registrar-signed XML, and nothing ever reclaims it.
 *
 * <p>This closes that gap out-of-band rather than by bolting a compensation saga onto the
 * hot path. Nothing here can fail a live batch, and there is no new Kafka topic or consumer.
 *
 * <p>The retention grace period exists so an operator can still inspect the artifacts of a
 * fresh failure while diagnosing it. Deletion is permanent, so the default is generous.
 *
 * <p>Not {@code @Transactional}: each item is purged in its own REQUIRES_NEW transaction by
 * {@link OrphanedArtifactPurgeTask}, so one bad item cannot roll back the rest of the sweep.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrphanedArtifactSweeper {

    private final TranscriptItemRepository itemRepository;
    private final OrphanedArtifactPurgeTask purgeTask;
    private final OrchestratorProperties props;

    @Scheduled(fixedDelayString = "${app.orchestrator.orphan-sweeper-interval-ms:3600000}")
    public void sweep() {
        Instant deadBefore = Instant.now()
                .minus(props.getOrphanRetentionHours(), ChronoUnit.HOURS);
        List<TranscriptItem> orphaned = itemRepository.findTerminalWithUnpurgedArtifacts(
                deadBefore, props.getOrphanSweepBatchSize());
        if (orphaned.isEmpty()) return;
        log.info("Orphan sweeper found {} terminal item(s) with unreclaimed artifacts", orphaned.size());
        for (TranscriptItem item : orphaned) {
            try {
                purgeTask.purge(item);
            } catch (Exception e) {
                // Leave artifacts_purged_at NULL so the next sweep retries this item.
                log.error("Orphan sweep failed for item {}: {}",
                        item.getTranscriptId(), e.getMessage(), e);
            }
        }
    }
}

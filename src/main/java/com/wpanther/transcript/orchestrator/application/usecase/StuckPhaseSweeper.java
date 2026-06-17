package com.wpanther.transcript.orchestrator.application.usecase;

import com.wpanther.transcript.orchestrator.domain.model.Batch;
import com.wpanther.transcript.orchestrator.domain.repository.BatchRepository;
import com.wpanther.transcript.orchestrator.infrastructure.config.OrchestratorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Timer-driven sweeper that finds automatic-phase batches which have
 * been awaiting a worker reply for longer than the configured timeout
 * and re-emits the corresponding command. The entry point is NOT
 * {@code @Transactional} — each batch is processed in its own
 * REQUIRES_NEW transaction by {@link StuckPhaseSweepTask} (M6 fix),
 * so a failure on one batch does not roll back another.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StuckPhaseSweeper {

    private final BatchRepository batchRepository;
    private final StuckPhaseSweepTask sweepTask;
    private final OrchestratorProperties props;

    @Scheduled(fixedDelayString = "${app.orchestrator.sweeper-interval-ms:60000}")
    public void sweep() {
        Instant stuckBefore = Instant.now()
            .minus(props.getStuckPhaseTimeoutMinutes(), ChronoUnit.MINUTES);
        List<Batch> stuck = batchRepository.findAutomaticPhaseStuckBefore(stuckBefore, 10);
        if (stuck.isEmpty()) return;
        log.warn("Sweeper found {} stuck automatic-phase batches", stuck.size());
        for (Batch batch : stuck) {
            try {
                sweepTask.reEmit(batch);
            } catch (Exception e) {
                log.error("Sweeper failed for batch {}: {}", batch.getId(), e.getMessage(), e);
            }
        }
    }
}

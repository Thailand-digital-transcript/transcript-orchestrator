package com.wpanther.transcript.orchestrator.application.usecase;

import com.wpanther.transcript.orchestrator.application.port.out.BatchSigningCommandPort;
import com.wpanther.transcript.orchestrator.application.port.out.PdfGenerationCommandPort;
import com.wpanther.transcript.orchestrator.domain.model.Batch;
import com.wpanther.transcript.orchestrator.domain.model.ItemStatus;
import com.wpanther.transcript.orchestrator.domain.model.SignerRole;
import com.wpanther.transcript.orchestrator.domain.model.SigningFormat;
import com.wpanther.transcript.orchestrator.domain.model.TranscriptItem;
import com.wpanther.transcript.orchestrator.domain.repository.BatchRepository;
import com.wpanther.transcript.orchestrator.domain.repository.TranscriptItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Per-batch re-emit for the stuck-phase sweeper. Runs in its own
 * REQUIRES_NEW transaction so a failure on one batch does not roll
 * back another (M6 fix).
 *
 * The signing/PDF command adapters are responsible for generating
 * a new correlationId and stamping the batch (awaitingReplyFor) —
 * this class does NOT clear awaitingReplyFor (M5 fix: the adapter
 * owns correlationId lifecycle).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StuckPhaseSweepTask {

    private final BatchRepository batchRepository;
    private final TranscriptItemRepository itemRepository;
    private final BatchSigningCommandPort signingCommandPort;
    private final PdfGenerationCommandPort pdfCommandPort;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reEmit(Batch batch) {
        // C1 fix: re-load the batch inside this REQUIRES_NEW TX so the version
        // field reflects the latest persisted state. The outer sweep() loaded
        // these objects outside any TX; another orchestrator instance may have
        // advanced the batch in the meantime, making the in-memory `version`
        // stale and causing OptimisticLockingFailureException on save.
        Batch fresh = batchRepository.findById(batch.getId()).orElse(null);
        if (fresh == null) {
            log.debug("Batch {} disappeared between sweep discovery and re-emit — skipping", batch.getId());
            return;
        }
        batch = fresh;
        switch (batch.getStatus()) {
            case REGISTRAR_SIGNING -> reSign(batch, ItemStatus.ASSIGNED, SignerRole.REGISTRAR, SigningFormat.XML);
            case DEAN_SIGNING      -> reSign(batch, ItemStatus.REGISTRAR_SIGNED, SignerRole.DEAN, SigningFormat.XML);
            case SEALING           -> reSign(batch, ItemStatus.DEAN_SIGNED, SignerRole.SEAL, SigningFormat.XML);
            case PDF_GENERATION    -> rePdf(batch);
            case PDF_SIGNING       -> reSign(batch, ItemStatus.PDF_RENDERED, SignerRole.SEAL, SigningFormat.PDF);
            default -> log.debug("Sweeper: batch {} in {} — not an automatic phase",
                batch.getId(), batch.getStatus());
        }
    }

    private void reSign(Batch batch, ItemStatus expectedStatus, SignerRole role, SigningFormat format) {
        List<TranscriptItem> items = itemRepository.findByBatchIdAndStatusIn(
            batch.getId(), List.of(expectedStatus));
        signingCommandPort.sendBatchSigningCommand(batch, items, role, format);
        batchRepository.save(batch);
    }

    private void rePdf(Batch batch) {
        List<TranscriptItem> items = itemRepository.findByBatchIdAndStatusIn(
            batch.getId(), List.of(ItemStatus.SEALED));
        pdfCommandPort.sendPdfGenerationCommand(batch, items);
        batchRepository.save(batch);
    }
}

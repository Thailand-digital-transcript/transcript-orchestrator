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
import org.springframework.stereotype.Component;
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
@Component
@RequiredArgsConstructor
public class StuckPhaseSweepTask {

    private final BatchRepository batchRepository;
    private final TranscriptItemRepository itemRepository;
    private final BatchSigningCommandPort signingCommandPort;
    private final PdfGenerationCommandPort pdfCommandPort;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reEmit(Batch batch) {
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

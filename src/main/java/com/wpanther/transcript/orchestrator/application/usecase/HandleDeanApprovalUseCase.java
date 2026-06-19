package com.wpanther.transcript.orchestrator.application.usecase;

import com.wpanther.transcript.orchestrator.application.dto.event.DeanApprovalEvent;
import com.wpanther.transcript.orchestrator.application.port.out.BatchSigningCommandPort;
import com.wpanther.transcript.orchestrator.domain.exception.BatchNotFoundException;
import com.wpanther.transcript.orchestrator.domain.model.*;
import com.wpanther.transcript.orchestrator.domain.repository.*;
import com.wpanther.transcript.orchestrator.domain.service.BatchStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Slf4j @Service @RequiredArgsConstructor
public class HandleDeanApprovalUseCase {
    private final BatchRepository batchRepository;
    private final TranscriptItemRepository itemRepository;
    private final BatchStateMachine stateMachine;
    private final BatchSigningCommandPort signingCommandPort;
    private final PendingDecisionRepository pendingDecisionRepository;

    @Transactional
    public void handle(DeanApprovalEvent event) {
        String decisionId = event.getDecisionId();
        if (decisionId != null && !pendingDecisionRepository.claim(UUID.fromString(decisionId))) {
            log.debug("Decision {} already processed; skipping", decisionId);
            return;
        }

        UUID batchId = UUID.fromString(event.getBatchId());
        Batch batch = batchRepository.findById(batchId)
            .orElseThrow(() -> new BatchNotFoundException(event.getBatchId()));

        if ("REJECT".equalsIgnoreCase(event.getDecision())) {
            stateMachine.deanReject(batch, event.getInstitutionCode(),
                event.getApprovedBy(), event.getApprovedAt(),
                event.getRejectionReason() != null ? event.getRejectionReason() : "Rejected by dean");
            batchRepository.save(batch);
            return;
        }

        stateMachine.deanApprove(batch, event.getInstitutionCode(),
            event.getApprovedBy(), event.getApprovedAt());

        if (batch.getStatus() != BatchStatus.DEAN_SIGNING) {
            // deanApprove was a no-op (already past PENDING_DEAN) — idempotent return
            return;
        }

        List<TranscriptItem> items = itemRepository.findByBatchIdAndStatusIn(batchId,
            List.of(ItemStatus.REGISTRAR_SIGNED));
        // I4 note: applyItemRejections only sees REGISTRAR_SIGNED items (the ones this
        // gate acts on). Items already in REJECTED/FAILED from the registrar gate are
        // not in this list. The "all rejected → cancel" decision therefore reflects
        // only the registrar-signed subset. If a future change widens the gate's
        // responsibility, switch to a full-roster query here.
        boolean cancelled = stateMachine.applyItemRejections(batch, items,
            event.getRejectedDocumentIds(), event.getApprovedBy(), event.getApprovedAt(),
            "Rejected by dean");

        itemRepository.saveAll(items);
        // Reuse the returned domain object: saveAndFlush bumped the @Version, and
        // the dispatch below mutates the batch again. Saving the stale-version
        // object a second time would fail optimistic locking.
        batch = batchRepository.save(batch);

        if (!cancelled) {
            List<TranscriptItem> healthy = items.stream().filter(TranscriptItem::isHealthy).toList();
            signingCommandPort.sendBatchSigningCommand(batch, healthy,
                SignerRole.DEAN, SigningFormat.XML);
            batchRepository.save(batch);
        }
    }
}

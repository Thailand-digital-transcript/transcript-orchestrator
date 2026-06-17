package com.wpanther.transcript.orchestrator.application.usecase;

import com.wpanther.transcript.orchestrator.application.dto.event.RegistrarApprovalEvent;
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
public class HandleRegistrarApprovalUseCase {
    private final BatchRepository batchRepository;
    private final TranscriptItemRepository itemRepository;
    private final BatchStateMachine stateMachine;
    private final BatchSigningCommandPort signingCommandPort;

    @Transactional
    public void handle(RegistrarApprovalEvent event) {
        UUID batchId = UUID.fromString(event.getBatchId());
        Batch batch = batchRepository.findById(batchId)
            .orElseThrow(() -> new BatchNotFoundException(event.getBatchId()));

        if ("REJECT".equalsIgnoreCase(event.getDecision())) {
            String reason = event.getRejectionReason() != null
                ? event.getRejectionReason() : "Rejected by registrar";
            stateMachine.registrarReject(batch, event.getInstitutionCode(),
                event.getApprovedBy(), event.getApprovedAt(), reason);
            batchRepository.save(batch);
            return;
        }

        // APPROVE: advance state, apply per-item rejections, then dispatch signing command
        List<TranscriptItem> items = itemRepository.findByBatchIdAndStatusIn(batchId,
            List.of(ItemStatus.ASSIGNED));
        stateMachine.registrarApprove(batch, event.getInstitutionCode(),
            event.getApprovedBy(), event.getApprovedAt());

        boolean cancelled = stateMachine.applyItemRejections(batch, items,
            event.getRejectedDocumentIds(), event.getApprovedBy(), event.getApprovedAt(),
            "Rejected by registrar");

        itemRepository.saveAll(items);
        batchRepository.save(batch);

        if (!cancelled) {
            List<TranscriptItem> healthy = items.stream().filter(TranscriptItem::isHealthy).toList();
            signingCommandPort.sendBatchSigningCommand(batch, healthy,
                SignerRole.REGISTRAR, SigningFormat.XML);
            batchRepository.save(batch); // persist awaitingReplyFor set by the adapter
        }
    }
}

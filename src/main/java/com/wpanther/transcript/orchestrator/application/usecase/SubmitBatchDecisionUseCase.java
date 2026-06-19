package com.wpanther.transcript.orchestrator.application.usecase;

import com.wpanther.transcript.orchestrator.application.dto.command.SubmitDecisionCommand;
import com.wpanther.transcript.orchestrator.application.port.out.ApprovalCommandPort;
import com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging.dto.OutboundApprovalCommand;
import com.wpanther.transcript.orchestrator.domain.exception.*;
import com.wpanther.transcript.orchestrator.domain.model.*;
import com.wpanther.transcript.orchestrator.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SubmitBatchDecisionUseCase {
    private final BatchRepository batchRepository;
    private final TranscriptItemRepository itemRepository;
    private final ApprovalCommandPort approvalCommandPort;
    private final PendingDecisionRepository pendingDecisionRepository;

    @Transactional
    public UUID submit(SubmitDecisionCommand cmd) {
        Batch batch = batchRepository.findById(cmd.batchId())
                .orElseThrow(() -> new BatchNotFoundException(cmd.batchId().toString()));

        // Privacy: spec §4.4 — a cross-institution caller must NOT be able to
        // distinguish "batch exists but belongs to another institution" from
        // "batch does not exist". Both conditions therefore throw the SAME
        // exception type (BatchNotFoundException) with the SAME message shape,
        // so ApprovalController's @ExceptionHandler produces a single,
        // indistinguishable 404 body. (This is the decision route's own check;
        // BatchStateMachine.requireInstitution is untouched and still throws
        // InstitutionMismatchException → 403 for create/assign/close.)
        if (!batch.getInstitutionCode().equals(cmd.institutionCode())) {
            throw new BatchNotFoundException(cmd.batchId().toString());
        }
        // Wrong gate → 409.
        if (batch.getStatus() != cmd.callerGate()) {
            throw new InvalidBatchStateException(
                    "Batch " + cmd.batchId() + " is " + batch.getStatus() + ", not " + cmd.callerGate());
        }
        // Decision shape → 400.
        String decision = cmd.decision() == null ? "" : cmd.decision().trim().toUpperCase();
        if (!decision.equals("APPROVE") && !decision.equals("REJECT")) {
            throw new DecisionValidationException("decision must be APPROVE or REJECT");
        }
        if (decision.equals("REJECT") && (cmd.rejectionReason() == null || cmd.rejectionReason().isBlank())) {
            throw new DecisionValidationException("rejectionReason is required when decision is REJECT");
        }
        List<String> rejectedIds = cmd.rejectedDocumentIds() == null ? List.of() : cmd.rejectedDocumentIds();
        if (!rejectedIds.isEmpty()) {
            Set<String> known = new HashSet<>();
            itemRepository.findByBatchId(cmd.batchId()).forEach(i -> known.add(i.getDocumentId()));
            for (String id : rejectedIds) {
                if (!known.contains(id)) {
                    throw new DecisionValidationException("Unknown rejectedDocumentId: " + id);
                }
            }
        }

        UUID decisionId = UUID.randomUUID();
        OutboundApprovalCommand command = new OutboundApprovalCommand(
                decisionId.toString(), cmd.batchId().toString(), decision,
                cmd.institutionCode(), cmd.approvedBy(), Instant.now(), rejectedIds,
                decision.equals("REJECT") ? cmd.rejectionReason() : null);

        // Outbox first so the FK references an existing row; both in this TX.
        UUID outboxEventId = approvalCommandPort.publish(batch.getStatus(), command, cmd.batchId().toString());
        pendingDecisionRepository.save(new PendingDecision(decisionId, cmd.batchId(),
                batch.getStatus(), decision, cmd.approvedBy(), cmd.institutionCode(),
                command.getRejectionReason(), rejectedIds, outboxEventId));
        return decisionId;
    }
}

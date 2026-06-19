package com.wpanther.transcript.orchestrator.application.port.out;

import com.wpanther.transcript.orchestrator.domain.model.BatchStatus;
import com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging.dto.OutboundApprovalCommand;
import java.util.UUID;

public interface ApprovalCommandPort {
    /** Publish via the outbox to approval.registrar (PENDING_REGISTRAR) or
     *  approval.dean (PENDING_DEAN). Returns the outbox event id. */
    UUID publish(BatchStatus gate, OutboundApprovalCommand command, String batchId);
}

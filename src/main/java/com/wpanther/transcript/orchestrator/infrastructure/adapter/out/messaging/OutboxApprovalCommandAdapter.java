package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging;

import com.wpanther.transcript.orchestrator.application.port.out.ApprovalCommandPort;
import com.wpanther.transcript.orchestrator.domain.model.BatchStatus;
import com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging.dto.OutboundApprovalCommand;
import com.wpanther.transcript.orchestrator.infrastructure.config.KafkaTopicProperties;
import com.wpanther.transcript.saga.infrastructure.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxApprovalCommandAdapter implements ApprovalCommandPort {
    private final OutboxService outboxService;
    private final KafkaTopicProperties topics;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID publish(BatchStatus gate, OutboundApprovalCommand command, String batchId) {
        String topic = gate == BatchStatus.PENDING_REGISTRAR
                ? topics.getApprovalRegistrar() : topics.getApprovalDean();
        return outboxService.saveWithRouting(command, "Batch", batchId, topic, batchId, null).getId();
    }
}

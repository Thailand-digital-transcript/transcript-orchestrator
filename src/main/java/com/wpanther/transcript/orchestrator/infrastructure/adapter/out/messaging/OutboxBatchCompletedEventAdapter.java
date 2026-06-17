package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging;

import com.wpanther.saga.infrastructure.outbox.OutboxService;
import com.wpanther.transcript.orchestrator.application.port.out.BatchCompletedEventPort;
import com.wpanther.transcript.orchestrator.domain.model.Batch;
import com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging.dto.BatchCompletedEvent;
import com.wpanther.transcript.orchestrator.infrastructure.config.KafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component @RequiredArgsConstructor
public class OutboxBatchCompletedEventAdapter implements BatchCompletedEventPort {
    private final OutboxService outboxService;
    private final KafkaTopicProperties topics;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishBatchCompleted(Batch batch) {
        BatchCompletedEvent event = new BatchCompletedEvent(
            batch.getId().toString(), batch.getInstitutionCode(),
            batch.getItemCount(), batch.getCompletedAt());
        outboxService.saveWithRouting(event, "Batch", batch.getId().toString(),
            topics.getBatchCompleted(), batch.getId().toString(), null);
    }
}

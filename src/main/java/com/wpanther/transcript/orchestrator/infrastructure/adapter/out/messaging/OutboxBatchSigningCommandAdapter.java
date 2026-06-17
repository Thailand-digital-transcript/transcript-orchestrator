package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging;

import com.wpanther.transcript.saga.infrastructure.outbox.OutboxService;
import com.wpanther.transcript.orchestrator.application.port.out.BatchSigningCommandPort;
import com.wpanther.transcript.orchestrator.domain.model.*;
import com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging.dto.OutboundBatchSigningCommand;
import com.wpanther.transcript.orchestrator.infrastructure.config.KafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Slf4j @Component @RequiredArgsConstructor
public class OutboxBatchSigningCommandAdapter implements BatchSigningCommandPort {
    private final OutboxService outboxService;
    private final KafkaTopicProperties topics;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void sendBatchSigningCommand(Batch batch, List<TranscriptItem> items,
            SignerRole signerRole, SigningFormat format) {
        String correlationId = UUID.randomUUID().toString();
        batch.applySigningStarted(correlationId);

        List<OutboundBatchSigningCommand.Item> commandItems = items.stream()
            // N2 note: per the downstream contract (transcript-signing
            // consumer), documentNumber is a deliberate duplicate of
            // documentId for backwards compatibility — verify against the
            // signing-service plan if the field is ever repurposed.
            .map(i -> new OutboundBatchSigningCommand.Item(
                i.getDocumentId(), i.getDocumentId(), i.currentSigningStorageKey()))
            .toList();

        OutboundBatchSigningCommand command = new OutboundBatchSigningCommand(
            batch.getId().toString(), correlationId, batch.getId().toString(),
            signerRole, format, commandItems);

        outboxService.saveWithRouting(command, "Batch", batch.getId().toString(),
            topics.getBatchSigningCommand(), batch.getId().toString(), null);

        log.info("Queued signing command batchId={} signerRole={} format={} {} items",
            batch.getId(), signerRole, format, items.size());
    }
}

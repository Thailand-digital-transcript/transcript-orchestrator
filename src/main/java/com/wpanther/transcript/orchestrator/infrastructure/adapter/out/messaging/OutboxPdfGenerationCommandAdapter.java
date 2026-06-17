package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging;

import com.wpanther.transcript.saga.infrastructure.outbox.OutboxService;
import com.wpanther.transcript.orchestrator.application.port.out.PdfGenerationCommandPort;
import com.wpanther.transcript.orchestrator.domain.model.*;
import com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging.dto.OutboundPdfGenerationCommand;
import com.wpanther.transcript.orchestrator.infrastructure.config.KafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Slf4j @Component @RequiredArgsConstructor
public class OutboxPdfGenerationCommandAdapter implements PdfGenerationCommandPort {
    private final OutboxService outboxService;
    private final KafkaTopicProperties topics;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void sendPdfGenerationCommand(Batch batch, List<TranscriptItem> items) {
        String correlationId = UUID.randomUUID().toString();
        batch.applyPdfGenerationStarted(correlationId);

        List<OutboundPdfGenerationCommand.Item> commandItems = items.stream()
            .map(i -> new OutboundPdfGenerationCommand.Item(
                i.getDocumentId(), i.getDocumentId(), i.getSealedXmlKey()))
            .toList();

        OutboundPdfGenerationCommand command = new OutboundPdfGenerationCommand(
            batch.getId().toString(), correlationId, batch.getId().toString(), commandItems);

        outboxService.saveWithRouting(command, "Batch", batch.getId().toString(),
            topics.getPdfGenerationCommand(), batch.getId().toString(), null);

        log.info("Queued PDF generation command batchId={} {} items", batch.getId(), items.size());
    }
}

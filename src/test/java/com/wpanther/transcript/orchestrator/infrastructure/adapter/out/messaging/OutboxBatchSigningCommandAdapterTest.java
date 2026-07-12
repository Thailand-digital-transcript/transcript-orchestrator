package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging;

import com.wpanther.transcript.orchestrator.domain.model.Batch;
import com.wpanther.transcript.orchestrator.domain.model.SignerRole;
import com.wpanther.transcript.orchestrator.domain.model.SigningFormat;
import com.wpanther.transcript.orchestrator.domain.model.StorageRef;
import com.wpanther.transcript.orchestrator.domain.model.TranscriptItem;
import com.wpanther.transcript.orchestrator.domain.service.TranscriptKeyResolver;
import com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging.dto.OutboundBatchSigningCommand;
import com.wpanther.transcript.orchestrator.infrastructure.config.KafkaTopicProperties;
import com.wpanther.transcript.saga.domain.model.IntegrationEvent;
import com.wpanther.transcript.saga.infrastructure.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Pins the (signerRole, format) -> target StorageRef switch in
 * {@link OutboxBatchSigningCommandAdapter#sendBatchSigningCommand}. Covers the four
 * combinations actually dispatched in this codebase (confirmed by re-checking every call
 * site: HandleRegistrarApprovalUseCase, HandleDeanApprovalUseCase, HandleSigningReplyUseCase,
 * HandlePdfGenerationReplyUseCase, and StuckPhaseSweepTask's re-emit mirror of the same four):
 * (REGISTRAR, XML), (DEAN, XML), (SEAL, XML), (SEAL, PDF).
 *
 * <p>Expected values are derived from a real {@link TranscriptKeyResolver} instance rather
 * than hand-computed strings, so this test tracks the resolver's actual behavior and would
 * fail if the switch ever routed a case to the wrong resolver method (e.g. REGISTRAR
 * accidentally producing a sealed-XML key).
 */
class OutboxBatchSigningCommandAdapterTest {

    private static final String ORIG = "2026/07/10/01/transcript-90993829998.xml";

    private final TranscriptKeyResolver resolver =
            new TranscriptKeyResolver("transcripts", "signed-transcripts", "transcript-pdfs");

    private OutboxService outboxService;
    private OutboxBatchSigningCommandAdapter adapter;

    @BeforeEach
    void setUp() {
        outboxService = mock(OutboxService.class);
        adapter = new OutboxBatchSigningCommandAdapter(outboxService, new KafkaTopicProperties(), resolver);
    }

    private TranscriptItem freshlyAssignedItem() {
        TranscriptItem item = TranscriptItem.register(
                "90993829998", "doc-1", "KMUTT", "01", ORIG);
        item.assign(java.util.UUID.randomUUID());
        return item;
    }

    private OutboundBatchSigningCommand.Item dispatchAndCapture(SignerRole role, SigningFormat format,
            TranscriptItem item) {
        Batch batch = Batch.create("batch-1", "KMUTT", "tester");

        adapter.sendBatchSigningCommand(batch, List.of(item), role, format);

        ArgumentCaptor<IntegrationEvent> captor = ArgumentCaptor.forClass(IntegrationEvent.class);
        verify(outboxService).saveWithRouting(captor.capture(), org.mockito.ArgumentMatchers.eq("Batch"),
                org.mockito.ArgumentMatchers.eq(batch.getId().toString()),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull());

        OutboundBatchSigningCommand command = (OutboundBatchSigningCommand) captor.getValue();
        assertThat(command.getItems()).hasSize(1);
        return command.getItems().get(0);
    }

    @Test
    void registrarXml_targetsTheRegistrarSignedKey() {
        OutboundBatchSigningCommand.Item result =
                dispatchAndCapture(SignerRole.REGISTRAR, SigningFormat.XML, freshlyAssignedItem());

        StorageRef expected = resolver.registrarSigned(ORIG);
        assertThat(result.getSourceBucket()).isEqualTo("transcripts");
        assertThat(result.getTargetStorageKey()).isEqualTo(expected.key());
    }

    @Test
    void deanXml_targetsTheDeanSignedKey() {
        TranscriptItem item = freshlyAssignedItem();
        item.markRegistrarSigned(resolver.registrarSigned(ORIG).key());

        OutboundBatchSigningCommand.Item result =
                dispatchAndCapture(SignerRole.DEAN, SigningFormat.XML, item);

        StorageRef expected = resolver.deanSigned(ORIG);
        assertThat(result.getSourceBucket()).isEqualTo(resolver.registrarSigned(ORIG).bucket());
        assertThat(result.getTargetStorageKey()).isEqualTo(expected.key());
    }

    @Test
    void sealXml_targetsTheSealedXmlKey_notTheSealedPdfKey() {
        TranscriptItem item = freshlyAssignedItem();
        item.markRegistrarSigned(resolver.registrarSigned(ORIG).key());
        item.markDeanSigned(resolver.deanSigned(ORIG).key());

        OutboundBatchSigningCommand.Item result =
                dispatchAndCapture(SignerRole.SEAL, SigningFormat.XML, item);

        StorageRef expected = resolver.sealed(ORIG);
        StorageRef notExpected = resolver.sealedPdf(ORIG);
        assertThat(result.getTargetStorageKey()).isEqualTo(expected.key());
        assertThat(result.getTargetStorageKey()).isNotEqualTo(notExpected.key());
        assertThat(result.getSourceBucket()).isEqualTo(resolver.deanSigned(ORIG).bucket());
    }

    @Test
    void sealPdf_targetsTheSealedPdfKey_notTheSealedXmlKey() {
        TranscriptItem item = freshlyAssignedItem();
        item.markRegistrarSigned(resolver.registrarSigned(ORIG).key());
        item.markDeanSigned(resolver.deanSigned(ORIG).key());
        item.markSealed(resolver.sealed(ORIG).key());
        item.markPdfRendered(resolver.renderedPdf(ORIG).key());

        OutboundBatchSigningCommand.Item result =
                dispatchAndCapture(SignerRole.SEAL, SigningFormat.PDF, item);

        StorageRef expected = resolver.sealedPdf(ORIG);
        StorageRef notExpected = resolver.sealed(ORIG);
        assertThat(result.getTargetStorageKey()).isEqualTo(expected.key());
        assertThat(result.getTargetStorageKey()).isNotEqualTo(notExpected.key());
        assertThat(result.getSourceBucket()).isEqualTo(resolver.renderedPdf(ORIG).bucket());
    }
}

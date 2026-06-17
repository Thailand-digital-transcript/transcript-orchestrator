package com.wpanther.transcript.orchestrator.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.transcript.orchestrator.application.dto.command.AssignItemsCommand;
import com.wpanther.transcript.orchestrator.application.dto.command.CreateBatchCommand;
import com.wpanther.transcript.orchestrator.application.dto.event.DeanApprovalEvent;
import com.wpanther.transcript.orchestrator.application.dto.event.InboundBatchSigningReplyEvent;
import com.wpanther.transcript.orchestrator.application.dto.event.InboundPdfGenerationReplyEvent;
import com.wpanther.transcript.orchestrator.application.dto.event.InboundStartSagaCommand;
import com.wpanther.transcript.orchestrator.application.dto.event.RegistrarApprovalEvent;
import com.wpanther.transcript.orchestrator.domain.model.BatchStatus;
import com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging.dto.BatchCompletedEvent;
import com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging.dto.OutboundBatchSigningCommand;
import com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging.dto.OutboundPdfGenerationCommand;
import com.wpanther.transcript.orchestrator.integration.support.IntegrationTestBase;
import com.wpanther.transcript.orchestrator.integration.support.KafkaTestHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full end-to-end happy path: register a transcript via the start-saga
 * command, build a batch, run the batch through every phase (registrar
 * signing, dean gate, dean signing, sealing, PDF generation, PDF signing)
 * and assert the batch reaches COMPLETED with a {@link BatchCompletedEvent}
 * on the completed topic.
 */
class OrchestratorHappyPathIT extends IntegrationTestBase {

    @Autowired ObjectMapper objectMapper;
    KafkaTestHelper kafka;

    @BeforeEach
    void setUp() {
        kafka = new KafkaTestHelper(KAFKA.getBootstrapServers(), objectMapper);
    }

    @Test
    void fullHappyPath_start_to_completed() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String txId = "tx-" + suffix;
        String docId = "doc-" + suffix;

        // 1. StartSagaCommand → transcript appears in pool
        kafka.send("saga.commands.orchestrator", txId,
            new InboundStartSagaCommand(txId, docId, "KMUTT", "REGULAR", "xmls/" + docId + ".xml"));

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(get("/api/v1/transcripts", String.class).getBody()).contains(docId));

        String itemId = itemIdFor(docId);
        String batchId = createBatch("Happy-" + suffix, "KMUTT");
        assignItems(batchId, List.of(itemId));
        closeBatch(batchId);
        assertBatchStatus(batchId, BatchStatus.PENDING_REGISTRAR);

        // 2. Registrar approves → REGISTRAR_SIGNING command dispatched
        kafka.send("approval.registrar", batchId,
            new RegistrarApprovalEvent(batchId, "APPROVE", "KMUTT", "reg", Instant.now(), List.of(), null));
        OutboundBatchSigningCommand c1 = kafka.pollFor("saga.command.transcript-signing.batch",
            "it-sign-1-" + suffix, OutboundBatchSigningCommand.class,
            c -> batchId.equals(c.getBatchId()), Duration.ofSeconds(30));
        assertThat(c1.getSignerRole().name()).isEqualTo("REGISTRAR");

        // 3. Signing reply → PENDING_DEAN
        kafka.send("saga.reply.transcript-signing", batchId,
            signingReply(c1.getCorrelationId(), batchId, docId, "reg-signed.xml"));
        assertBatchStatus(batchId, BatchStatus.PENDING_DEAN);

        // 4. Dean approves → DEAN_SIGNING command
        kafka.send("approval.dean", batchId,
            new DeanApprovalEvent(batchId, "APPROVE", "KMUTT", "dean", Instant.now(), List.of(), null));
        OutboundBatchSigningCommand c2 = kafka.pollFor("saga.command.transcript-signing.batch",
            "it-sign-2-" + suffix, OutboundBatchSigningCommand.class,
            c -> batchId.equals(c.getBatchId()) && "DEAN".equals(c.getSignerRole().name()),
            Duration.ofSeconds(30));

        // 5. Dean signing reply → SEALING command
        kafka.send("saga.reply.transcript-signing", batchId,
            signingReply(c2.getCorrelationId(), batchId, docId, "dean-signed.xml"));
        OutboundBatchSigningCommand c3 = kafka.pollFor("saga.command.transcript-signing.batch",
            "it-sign-3-" + suffix, OutboundBatchSigningCommand.class,
            c -> batchId.equals(c.getBatchId()) && "SEAL".equals(c.getSignerRole().name())
              && "XML".equals(c.getFormat().name()), Duration.ofSeconds(30));

        // 6. Sealing reply → PDF_GENERATION command
        kafka.send("saga.reply.transcript-signing", batchId,
            signingReply(c3.getCorrelationId(), batchId, docId, "sealed.xml"));
        OutboundPdfGenerationCommand c4 = kafka.pollFor("saga.command.transcript-pdf-generation",
            "it-pdf-" + suffix, OutboundPdfGenerationCommand.class,
            c -> batchId.equals(c.getBatchId()), Duration.ofSeconds(30));

        // 7. PDF reply → PDF_SIGNING command
        kafka.send("saga.reply.transcript-pdf-generation", batchId,
            pdfReply(c4.getCorrelationId(), batchId, docId, "transcript.pdf"));
        OutboundBatchSigningCommand c5 = kafka.pollFor("saga.command.transcript-signing.batch",
            "it-sign-5-" + suffix, OutboundBatchSigningCommand.class,
            c -> batchId.equals(c.getBatchId()) && "PDF".equals(c.getFormat().name()),
            Duration.ofSeconds(30));

        // 8. PDF signing reply → COMPLETED + BatchCompletedEvent
        kafka.send("saga.reply.transcript-signing", batchId,
            signingReply(c5.getCorrelationId(), batchId, docId, "signed.pdf"));
        assertBatchStatus(batchId, BatchStatus.COMPLETED);
        kafka.pollFor("transcript.batch.completed", "it-done-" + suffix,
            BatchCompletedEvent.class, c -> batchId.equals(c.getBatchId()), Duration.ofSeconds(20));
    }

    // --- helpers ---

    private <T> ResponseEntity<T> get(String path, Class<T> type) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-API-Key", "test-key");
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(h), type);
    }

    private <T> HttpEntity<T> withKey(T body) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-API-Key", "test-key");
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, h);
    }

    private String createBatch(String name, String inst) {
        CreateBatchCommand cmd = new CreateBatchCommand();
        cmd.setName(name);
        cmd.setInstitutionCode(inst);
        cmd.setCreatedBy("test");
        return restTemplate.exchange("/api/v1/batches", HttpMethod.POST, withKey(cmd), Map.class)
            .getBody().get("batchId").toString();
    }

    private void assignItems(String batchId, List<String> ids) {
        AssignItemsCommand cmd = new AssignItemsCommand();
        cmd.setItemIds(ids.stream().map(UUID::fromString).toList());
        restTemplate.exchange("/api/v1/batches/" + batchId + "/items",
            HttpMethod.POST, withKey(cmd), Void.class);
    }

    private void closeBatch(String batchId) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-API-Key", "test-key");
        h.set("X-Closed-By", "test");
        restTemplate.exchange("/api/v1/batches/" + batchId + "/close",
            HttpMethod.POST, new HttpEntity<>(h), Map.class);
    }

    private String itemIdFor(String docId) {
        ResponseEntity<Map[]> r = get("/api/v1/transcripts", Map[].class);
        return Arrays.stream(r.getBody())
            .filter(m -> docId.equals(m.get("documentId")))
            .map(m -> m.get("id").toString())
            .findFirst()
            .orElseThrow();
    }

    private void assertBatchStatus(String batchId, BatchStatus expected) {
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(get("/api/v1/batches/" + batchId, Map.class).getBody().get("status"))
                .isEqualTo(expected.name()));
    }

    private InboundBatchSigningReplyEvent signingReply(String corrId, String batchId,
            String docId, String key) {
        return new InboundBatchSigningReplyEvent(batchId, "sign-xml", corrId, "SUCCESS", null, batchId,
            List.of(new InboundBatchSigningReplyEvent.ItemResult(docId, "SIGNED", key, 5000L, null)));
    }

    private InboundPdfGenerationReplyEvent pdfReply(String corrId, String batchId,
            String docId, String key) {
        return new InboundPdfGenerationReplyEvent(batchId, "generate-transcript-pdf", corrId,
            "SUCCESS", null, batchId,
            List.of(new InboundPdfGenerationReplyEvent.ItemResult(docId, "GENERATED", key, 50000L, null)));
    }
}

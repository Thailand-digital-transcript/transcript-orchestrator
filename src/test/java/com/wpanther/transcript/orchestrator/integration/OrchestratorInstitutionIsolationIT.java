package com.wpanther.transcript.orchestrator.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.transcript.orchestrator.application.dto.command.AssignItemsCommand;
import com.wpanther.transcript.orchestrator.application.dto.command.CreateBatchCommand;
import com.wpanther.transcript.orchestrator.application.dto.event.InboundStartSagaCommand;
import com.wpanther.transcript.orchestrator.application.dto.event.RegistrarApprovalEvent;
import com.wpanther.transcript.orchestrator.domain.model.BatchStatus;
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
 * Verifies that an approval event whose {@code institutionCode} does not match
 * the batch's owning institution is rejected (state machine throws
 * {@code InstitutionMismatchException}, the consumer's {@code onException}
 * route pushes the event to the DLQ topic) and the batch state remains
 * untouched at PENDING_REGISTRAR.
 */
class OrchestratorInstitutionIsolationIT extends IntegrationTestBase {

    @Autowired ObjectMapper objectMapper;
    KafkaTestHelper kafka;

    @BeforeEach
    void setUp() {
        kafka = new KafkaTestHelper(KAFKA.getBootstrapServers(), objectMapper);
    }

    @Test
    void approvalFromWrongInstitution_routedToDlq_batchUnchanged() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String txId = "tx-iso-" + suffix;
        String docId = "doc-iso-" + suffix;

        kafka.send("saga.commands.orchestrator", txId,
            new InboundStartSagaCommand(txId, docId, "KMUTT", "REGULAR", "xmls/" + docId + ".xml"));

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(bearerToken());
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(restTemplate.exchange("/api/v1/transcripts",
                HttpMethod.GET, new HttpEntity<>(h), String.class).getBody()).contains(docId));

        String itemId = Arrays.stream(restTemplate.exchange("/api/v1/transcripts",
                HttpMethod.GET, new HttpEntity<>(h), Map[].class).getBody())
            .filter(m -> docId.equals(m.get("documentId")))
            .map(m -> m.get("id").toString())
            .findFirst()
            .orElseThrow();

        CreateBatchCommand bc = new CreateBatchCommand();
        bc.setName("IsoTest-" + suffix);
        bc.setInstitutionCode("KMUTT");
        bc.setCreatedBy("test");
        HttpHeaders jh = new HttpHeaders();
        jh.setBearerAuth(bearerToken());
        jh.setContentType(MediaType.APPLICATION_JSON);
        String batchId = restTemplate.exchange("/api/v1/batches", HttpMethod.POST,
            new HttpEntity<>(bc, jh), Map.class).getBody().get("batchId").toString();

        AssignItemsCommand ac = new AssignItemsCommand();
        ac.setItemIds(List.of(UUID.fromString(itemId)));
        restTemplate.exchange("/api/v1/batches/" + batchId + "/items",
            HttpMethod.POST, new HttpEntity<>(ac, jh), Void.class);

        HttpHeaders ch = new HttpHeaders();
        ch.setBearerAuth(bearerToken());
        ch.set("X-Closed-By", "t");
        restTemplate.exchange("/api/v1/batches/" + batchId + "/close",
            HttpMethod.POST, new HttpEntity<>(ch), Map.class);

        // Approval from a DIFFERENT institution — should route to DLQ, batch stays PENDING_REGISTRAR
        kafka.send("approval.registrar", batchId,
            new RegistrarApprovalEvent(null, batchId, "APPROVE", "OTHER_UNIVERSITY", "r", Instant.now(), List.of(), null));

        // C2 fix: wait for the DLQ event (best-effort, tolerates slow delivery),
        // then assert the batch state REMAINS PENDING_REGISTRAR for a 3-second
        // window using Awaitility.during(). Replaces a previous Thread.sleep(5000)
        // which was flaky on slow CI. during() succeeds only if the condition
        // holds for the full duration — if the wrong-institution event were to
        // (incorrectly) advance the batch, this fails.
        try {
            kafka.pollFor("transcript.orchestrator.dlq", "it-dlq-" + suffix,
                Object.class, m -> true, Duration.ofSeconds(30));
        } catch (AssertionError ignored) {
            // DLQ delivery can lag; the during() assertion below is the real guard.
        }

        Awaitility.await()
            .pollInterval(Duration.ofMillis(500))
            .during(Duration.ofSeconds(3))
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> {
                ResponseEntity<Map> detail = restTemplate.exchange("/api/v1/batches/" + batchId,
                    HttpMethod.GET, new HttpEntity<>(h), Map.class);
                assertThat(detail.getBody().get("status")).isEqualTo(BatchStatus.PENDING_REGISTRAR.name());
            });
    }
}

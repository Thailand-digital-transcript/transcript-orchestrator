package com.wpanther.transcript.orchestrator.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.transcript.orchestrator.application.dto.command.AssignItemsCommand;
import com.wpanther.transcript.orchestrator.application.dto.command.CreateBatchCommand;
import com.wpanther.transcript.orchestrator.application.dto.event.InboundStartSagaCommand;
import com.wpanther.transcript.orchestrator.application.dto.event.RegistrarApprovalEvent;
import com.wpanther.transcript.orchestrator.domain.model.BatchStatus;
import com.wpanther.transcript.orchestrator.domain.model.ItemStatus;
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
 * Registrar rejection paths. Verifies that a whole-batch REJECT cancels the
 * batch, and that a per-item subset reject from the registrar leaves the
 * remaining (non-rejected) items ASSIGNED while the batch advances to
 * REGISTRAR_SIGNING.
 */
class OrchestratorRejectionIT extends IntegrationTestBase {

    @Autowired ObjectMapper objectMapper;
    KafkaTestHelper kafka;

    @BeforeEach
    void setUp() {
        kafka = new KafkaTestHelper(KAFKA.getBootstrapServers(), objectMapper);
    }

    @Test
    void wholeRegistrarReject_cancelsBatch() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String txId = "tx-r-" + suffix;
        String docId = "doc-r-" + suffix;

        kafka.send("saga.commands.orchestrator", txId,
            new InboundStartSagaCommand(txId, docId, "KMUTT", "REGULAR", "xmls/" + docId + ".xml"));
        awaitItemInPool(docId);

        String batchId = createBatch("RejectTest-" + suffix, "KMUTT");
        assignItems(batchId, List.of(itemIdFor(docId)));
        closeBatch(batchId);

        kafka.send("approval.registrar", batchId,
            new RegistrarApprovalEvent(null, batchId, "REJECT", "KMUTT", "reg", Instant.now(), null, "Not ready"));

        assertBatchStatus(batchId, BatchStatus.CANCELLED);
    }

    @Test
    void registrarSubsetReject_onlyRejectedDocRejected_batchContinues() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String txId1 = "tx-s1-" + suffix;
        String docId1 = "doc-s1-" + suffix;
        String txId2 = "tx-s2-" + suffix;
        String docId2 = "doc-s2-" + suffix;

        kafka.send("saga.commands.orchestrator", txId1,
            new InboundStartSagaCommand(txId1, docId1, "KMUTT", "REGULAR", "xmls/" + docId1 + ".xml"));
        kafka.send("saga.commands.orchestrator", txId2,
            new InboundStartSagaCommand(txId2, docId2, "KMUTT", "REGULAR", "xmls/" + docId2 + ".xml"));

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            String body = get("/api/v1/transcripts", String.class).getBody();
            assertThat(body).contains(docId1).contains(docId2);
        });

        String batchId = createBatch("SubReject-" + suffix, "KMUTT");
        assignItems(batchId, List.of(itemIdFor(docId1), itemIdFor(docId2)));
        closeBatch(batchId);

        // Registrar approves the batch but rejects doc-1 by id.
        kafka.send("approval.registrar", batchId,
            new RegistrarApprovalEvent(null, batchId, "APPROVE", "KMUTT", "reg", Instant.now(),
                List.of(docId1), null));

        // Batch advances to REGISTRAR_SIGNING (not CANCELLED — doc-2 is still ASSIGNED).
        assertBatchStatus(batchId, BatchStatus.REGISTRAR_SIGNING);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>)
                get("/api/v1/batches/" + batchId, Map.class).getBody().get("items");
            assertThat(items.stream()
                .filter(i -> docId1.equals(i.get("documentId")))
                .map(i -> i.get("status"))
                .findFirst()
                .orElseThrow())
                .isEqualTo(ItemStatus.REJECTED.name());
        });
    }

    // --- helpers ---

    private <T> ResponseEntity<T> get(String path, Class<T> type) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(bearerToken());
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(h), type);
    }

    private <T> HttpEntity<T> withBearer(T body) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(bearerToken());
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, h);
    }

    private void awaitItemInPool(String docId) {
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(get("/api/v1/transcripts", String.class).getBody()).contains(docId));
    }

    private String itemIdFor(String docId) {
        return Arrays.stream(get("/api/v1/transcripts", Map[].class).getBody())
            .filter(m -> docId.equals(m.get("documentId")))
            .map(m -> m.get("id").toString())
            .findFirst()
            .orElseThrow();
    }

    private String createBatch(String name, String inst) {
        CreateBatchCommand cmd = new CreateBatchCommand();
        cmd.setName(name);
        cmd.setInstitutionCode(inst);
        cmd.setCreatedBy("test");
        return restTemplate.exchange("/api/v1/batches", HttpMethod.POST, withBearer(cmd), Map.class)
            .getBody().get("batchId").toString();
    }

    private void assignItems(String batchId, List<String> ids) {
        AssignItemsCommand cmd = new AssignItemsCommand();
        cmd.setItemIds(ids.stream().map(UUID::fromString).toList());
        restTemplate.exchange("/api/v1/batches/" + batchId + "/items",
            HttpMethod.POST, withBearer(cmd), Void.class);
    }

    private void closeBatch(String batchId) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(bearerToken());
        h.set("X-Closed-By", "test");
        restTemplate.exchange("/api/v1/batches/" + batchId + "/close",
            HttpMethod.POST, new HttpEntity<>(h), Map.class);
    }

    private void assertBatchStatus(String batchId, BatchStatus expected) {
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(get("/api/v1/batches/" + batchId, Map.class).getBody().get("status"))
                .isEqualTo(expected.name()));
    }
}

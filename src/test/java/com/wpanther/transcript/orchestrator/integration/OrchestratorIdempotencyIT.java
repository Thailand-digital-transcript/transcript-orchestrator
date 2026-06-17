package com.wpanther.transcript.orchestrator.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.transcript.orchestrator.application.dto.event.InboundStartSagaCommand;
import com.wpanther.transcript.orchestrator.integration.support.IntegrationTestBase;
import com.wpanther.transcript.orchestrator.integration.support.KafkaTestHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that re-delivering the same {@link InboundStartSagaCommand}
 * (same transcriptId) does not register a second item. The
 * {@code RegisterTranscriptUseCase} short-circuits on duplicate transcriptId
 * — the second delivery is a no-op.
 */
class OrchestratorIdempotencyIT extends IntegrationTestBase {

    @Autowired ObjectMapper objectMapper;
    KafkaTestHelper kafka;

    @BeforeEach
    void setUp() {
        kafka = new KafkaTestHelper(KAFKA.getBootstrapServers(), objectMapper);
    }

    @Test
    void duplicateStartSagaCommand_registersItemOnce() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String txId = "tx-idem-" + suffix;
        String docId = "doc-idem-" + suffix;

        InboundStartSagaCommand cmd =
            new InboundStartSagaCommand(txId, docId, "KMUTT", "REGULAR", "xmls/" + docId + ".xml");

        kafka.send("saga.commands.orchestrator", txId, cmd);
        kafka.send("saga.commands.orchestrator", txId, cmd);  // duplicate

        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            HttpHeaders h = new HttpHeaders();
            h.set("X-API-Key", "test-key");
            ResponseEntity<Map[]> r = restTemplate.exchange("/api/v1/transcripts",
                HttpMethod.GET, new HttpEntity<>(h), Map[].class);
            long count = Arrays.stream(r.getBody())
                .filter(m -> docId.equals(m.get("documentId")))
                .count();
            assertThat(count).isEqualTo(1);
        });
    }
}

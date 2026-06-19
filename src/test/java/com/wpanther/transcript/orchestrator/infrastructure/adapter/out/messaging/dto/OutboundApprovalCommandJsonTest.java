package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.messaging.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class OutboundApprovalCommandJsonTest {
    @Test
    void serializesConsumerFieldNames() throws Exception {
        var cmd = new OutboundApprovalCommand("d-1", "b-1", "APPROVE", "01110",
                "alice", Instant.parse("2026-06-18T00:00:00Z"), List.of(), null);
        JsonNode n = new ObjectMapper().findAndRegisterModules().valueToTree(cmd);
        assertThat(n.get("decisionId").asText()).isEqualTo("d-1");
        assertThat(n.get("batchId").asText()).isEqualTo("b-1");
        assertThat(n.get("decision").asText()).isEqualTo("APPROVE");
        assertThat(n.has("eventId")).isTrue(); // envelope present; consumer ignores it
    }
}

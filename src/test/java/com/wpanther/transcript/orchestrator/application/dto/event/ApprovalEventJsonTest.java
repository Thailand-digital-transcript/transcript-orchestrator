package com.wpanther.transcript.orchestrator.application.dto.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ApprovalEventJsonTest {
    private final ObjectMapper m = new ObjectMapper().findAndRegisterModules();

    @Test
    void deserializesDecisionIdAndIgnoresEnvelope() throws Exception {
        String json = """
            {"decisionId":"d-1","batchId":"b-1","decision":"APPROVE","institutionCode":"01110",
             "approvedBy":"alice","approvedAt":"2026-06-18T00:00:00Z","rejectedDocumentIds":[],
             "eventId":"11111111-1111-1111-1111-111111111111","eventType":"X","occurredAt":"2026-06-18T00:00:00Z"}""";
        RegistrarApprovalEvent e = m.readValue(json, RegistrarApprovalEvent.class);
        assertThat(e.getDecisionId()).isEqualTo("d-1");
        assertThat(e.getBatchId()).isEqualTo("b-1");
    }
}

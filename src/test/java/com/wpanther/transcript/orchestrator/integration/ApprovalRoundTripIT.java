package com.wpanther.transcript.orchestrator.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.transcript.orchestrator.application.dto.event.RegistrarApprovalEvent;
import com.wpanther.transcript.orchestrator.domain.model.Batch;
import com.wpanther.transcript.orchestrator.domain.model.BatchStatus;
import com.wpanther.transcript.orchestrator.domain.model.ItemStatus;
import com.wpanther.transcript.orchestrator.domain.model.TranscriptItem;
import com.wpanther.transcript.orchestrator.domain.repository.BatchRepository;
import com.wpanther.transcript.orchestrator.domain.repository.TranscriptItemRepository;
import com.wpanther.transcript.orchestrator.integration.support.IntegrationTestBase;
import com.wpanther.transcript.orchestrator.integration.support.KafkaTestHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase A end-to-end round-trip IT (task A11). Proves the full
 * controller → use case → transactional outbox → {@code OutboxRelayRoute} →
 * {@code ApprovalConsumerRoute} → state-machine loop for both human gates,
 * plus the atomic single-winner dedupe on {@code PendingDecisionRepository.claim}.
 *
 * <h3>Scenarios</h3>
 * <ol>
 *   <li><b>Registrar APPROVE</b> via {@code POST /api/v1/batches/{id}/decision}
 *       with a {@code ROLE_REGISTRAR} JWT → 202; poll until
 *       {@code batch.status == REGISTRAR_SIGNING} AND
 *       {@code registrarApprovedBy == <JWT preferred_username>}.</li>
 *   <li><b>Dean APPROVE</b> on a {@code PENDING_DEAN} batch with a
 *       {@code ROLE_DEAN} JWT → 202; poll until {@code batch.status == DEAN_SIGNING}
 *       AND {@code deanApprovedBy == <JWT preferred_username>}.</li>
 *   <li><b>Atomic dedupe</b>: publish the SAME {@code decisionId} event twice to
 *       {@code approval.registrar}; the batch advances exactly once, the second
 *       delivery is a no-op, and the registrar gate does NOT land in the DLQ.</li>
 * </ol>
 *
 * <h3>Post-relay polling cadence</h3>
 * The state transition only fires after the {@code OutboxRelayRoute} timer
 * (5s) drains the outbox to Kafka and the consumer runs. The default
 * {@code Awaitility.await()} (100ms × 10s) flakes against that. Every post-relay
 * assertion is therefore pinned to {@code atMost(30s).pollInterval(1s)} per the
 * A11 plan review.
 *
 * <h3>Mock JWT wiring</h3>
 * Identical to {@code DecisionEndpointIT}: the test profile has no
 * {@code KEYCLOAK_ISSUER_URI}, so only the API-key chain is active. We construct
 * a real {@link JwtAuthenticationToken} carrying the {@code preferred_username},
 * {@code institution_code}, and role authorities the production JWT converter
 * would emit, and inject it via
 * {@link SecurityMockMvcRequestPostProcessors#authentication}. The chain's
 * {@code hasAnyRole("REGISTRAR","DEAN")} rule is satisfied and
 * {@code CallerContext} sees the JWT caller exactly as in production.
 */
@AutoConfigureMockMvc
class ApprovalRoundTripIT extends IntegrationTestBase {

    /** Institution code stamped on both the JWT claim and the seeded batches. */
    private static final String INSTITUTION = "01110";
    /** DLQ topic (must match {@code KafkaTopicProperties.dlq} default). */
    private static final String DLQ_TOPIC = "transcript.orchestrator.dlq";
    /** Registrar consumer topic (must match {@code KafkaTopicProperties.approvalRegistrar}). */
    private static final String APPROVAL_REGISTRAR_TOPIC = "approval.registrar";

    @Autowired MockMvc mockMvc;
    @Autowired BatchRepository batchRepository;
    @Autowired TranscriptItemRepository itemRepository;
    @Autowired ObjectMapper objectMapper;

    private KafkaTestHelper kafka;

    @BeforeEach
    void setUp() {
        kafka = new KafkaTestHelper(KAFKA.getBootstrapServers(), objectMapper);
    }

    // -------------------- Scenario 1: registrar APPROVE round-trip --------------------

    @Test
    void registrarApprove_roundTripsToRegistrarSigning_andStampsApprovedBy() throws Exception {
        String username = "registrar-user-" + UUID.randomUUID();
        Batch batch = seedPendingRegistrarBatch();

        // POST /decision with ROLE_REGISTRAR → 202, decisionId returned.
        mockMvc.perform(post("/api/v1/batches/" + batch.getId() + "/decision")
                .with(jwt(username, INSTITUTION, "ROLE_REGISTRAR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(decisionBody("APPROVE", null, null)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.batchId").value(batch.getId().toString()))
            .andExpect(jsonPath("$.decisionId").exists())
            .andExpect(jsonPath("$.status").value("PENDING_REGISTRAR"))
            .andExpect(jsonPath("$.accepted").value(true));

        // Outbox relay (5s) → approval.registrar → HandleRegistrarApprovalUseCase
        // → batch advances to REGISTRAR_SIGNING with registrarApprovedBy = username.
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                Map<String, Object> detail = fetchDetail(batch.getId());
                assertThat(detail.get("status")).isEqualTo(BatchStatus.REGISTRAR_SIGNING.name());
                assertThat(detail.get("registrarApprovedBy")).isEqualTo(username);
            });
    }

    // -------------------- Scenario 2: dean APPROVE round-trip --------------------

    @Test
    void deanApprove_roundTripsToDeanSigning_andStampsApprovedBy() throws Exception {
        String username = "dean-user-" + UUID.randomUUID();
        // Seed directly to PENDING_DEAN with one REGISTRAR_SIGNED item so the
        // dean consumer does not cancel the batch (applyItemRejections cancels
        // when every item in its query window is terminal, and an empty
        // REGISTRAR_SIGNED window would trip that).
        Batch batch = seedPendingDeanBatch();

        mockMvc.perform(post("/api/v1/batches/" + batch.getId() + "/decision")
                .with(jwt(username, INSTITUTION, "ROLE_DEAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(decisionBody("APPROVE", null, null)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.batchId").value(batch.getId().toString()))
            .andExpect(jsonPath("$.decisionId").exists())
            .andExpect(jsonPath("$.status").value("PENDING_DEAN"))
            .andExpect(jsonPath("$.accepted").value(true));

        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                Map<String, Object> detail = fetchDetail(batch.getId());
                assertThat(detail.get("status")).isEqualTo(BatchStatus.DEAN_SIGNING.name());
                assertThat(detail.get("deanApprovedBy")).isEqualTo(username);
            });
    }

    // -------------------- Scenario 3: atomic dedupe (same decisionId twice) --------------------

    @Test
    void replayingSameDecisionId_advancesOnce_andDoesNotDlq() throws Exception {
        String username = "registrar-dedupe-" + UUID.randomUUID();
        Batch batch = seedPendingRegistrarBatch();

        // 1. POST /decision → capture the generated decisionId.
        String response = mockMvc.perform(post("/api/v1/batches/" + batch.getId() + "/decision")
                .with(jwt(username, INSTITUTION, "ROLE_REGISTRAR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(decisionBody("APPROVE", null, null)))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        String decisionId = objectMapper.readTree(response).get("decisionId").asText();

        // 2. Publish the SAME decisionId event twice directly to approval.registrar.
        //    These race the relay-published copy; whichever claims first wins, the
        //    remaining two (relay + 2 manual) all observe claim()==false and no-op.
        RegistrarApprovalEvent replay = new RegistrarApprovalEvent(
            decisionId, batch.getId().toString(), "APPROVE", INSTITUTION,
            username, Instant.now(), List.of(), null);
        kafka.send(APPROVAL_REGISTRAR_TOPIC, batch.getId().toString(), replay);
        kafka.send(APPROVAL_REGISTRAR_TOPIC, batch.getId().toString(), replay);

        // 3. Exactly one state change: batch advanced to REGISTRAR_SIGNING and
        //    stayed there (no double-advance, no cancellation, no FAILED).
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                Map<String, Object> detail = fetchDetail(batch.getId());
                assertThat(detail.get("status")).isEqualTo(BatchStatus.REGISTRAR_SIGNING.name());
                assertThat(detail.get("registrarApprovedBy")).isEqualTo(username);
            });

        // 4. The duplicate deliveries must NOT have thrown inside the consumer
        //    route (which would route the message to the DLQ via onException).
        //    Assert no DLQ message referencing this batch arrives within a short
        //    window. pollFor throws AssertionError on timeout — the success case.
        assertNoDlqMessageForBatch(batch.getId().toString());
    }

    // ----------------- helpers -----------------

    /**
     * Asserts that NO DLQ message carrying {@code batchId} arrives on
     * {@link #DLQ_TOPIC} within a short window. The {@code ApprovalConsumerRoute}
     * {@code onException} handler routes any consumer failure to the DLQ with the
     * original Kafka key ({@code ${headers[kafka.KEY]}}, which is the batch id) and
     * the original event JSON as the body. We therefore scan the DLQ value for the
     * batch id. {@link KafkaTestHelper#pollFor} throws {@link AssertionError} on
     * timeout — exactly the success outcome here, so we assert that the throw
     * occurred and that no matching record was observed along the way.
     */
    private void assertNoDlqMessageForBatch(String batchId) {
        boolean[] found = {false};
        assertThatThrownBy(() -> kafka.pollFor(
                DLQ_TOPIC,
                "dlq-check-" + UUID.randomUUID(),
                String.class,
                msg -> {
                    if (msg != null && msg.contains(batchId)) {
                        found[0] = true;
                        return true; // satisfy predicate → pollFor returns (no throw)
                    }
                    return false; // keep polling until timeout
                },
                Duration.ofSeconds(3)))
            .as("Duplicate decisionId must not land in the DLQ")
            .isInstanceOf(AssertionError.class);
        assertThat(found[0])
            .as("A DLQ message referencing batch %s was observed", batchId)
            .isFalse();
    }

    /** GET /api/v1/batches/{id} as the registrar JWT and return the detail map. */
    private Map<String, Object> fetchDetail(UUID batchId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/batches/" + batchId)
                .with(jwt("poller", INSTITUTION, "ROLE_REGISTRAR"))
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    /**
     * Builds a {@link JwtAuthenticationToken} post-processor carrying the given
     * username, institution code, and authorities — matching what
     * {@code SecurityConfig#jwtConverter()} would produce from a real Keycloak
     * JWT. Mirrors {@code DecisionEndpointIT#jwt}.
     */
    private static RequestPostProcessor jwt(
            String username, String institutionCode, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
            .map(SimpleGrantedAuthority::new)
            .map(a -> (GrantedAuthority) a)
            .toList();
        Map<String, Object> claims = new HashMap<>();
        claims.put("preferred_username", username);
        claims.put("institution_code", institutionCode);
        claims.put("sub", username);
        Jwt jwtToken = Jwt.withTokenValue("mock-token-value")
            .header("alg", "none")
            .claims(c -> c.putAll(claims))
            .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwtToken, authorities);
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }

    private String decisionBody(String decision, List<String> rejectedIds, String reason)
            throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("decision", decision);
        map.put("rejectedDocumentIds", rejectedIds);
        map.put("rejectionReason", reason);
        return objectMapper.writeValueAsString(map);
    }

    /**
     * Seeds a closed {@code PENDING_REGISTRAR} batch with one {@code ASSIGNED}
     * item. The non-empty item list prevents {@code applyItemRejections} from
     * cancelling the batch on an APPROVE with no rejected ids.
     */
    private Batch seedPendingRegistrarBatch() {
        Batch b = Batch.create("a11-reg-" + UUID.randomUUID(), INSTITUTION, "seeder");
        b.applyClose("seeder", Instant.now());
        Batch saved = batchRepository.save(b);
        seedItem(saved, ItemStatus.ASSIGNED);
        return saved;
    }

    /**
     * Seeds a {@code PENDING_DEAN} batch with one {@code REGISTRAR_SIGNED} item.
     * Reaches {@code PENDING_DEAN} by applying the registrar-approved and
     * registrar-signing-complete transitions in-memory before persisting. The
     * {@code REGISTRAR_SIGNED} item keeps the dean consumer's item query window
     * non-empty so the batch is not cancelled.
     */
    private Batch seedPendingDeanBatch() {
        Batch b = Batch.create("a11-dean-" + UUID.randomUUID(), INSTITUTION, "seeder");
        b.applyClose("seeder", Instant.now());
        b.applyRegistrarApproved("prior-registrar", Instant.now());
        b.applyRegistrarSigningComplete(); // REGISTRAR_SIGNING → PENDING_DEAN
        Batch saved = batchRepository.save(b);
        seedItem(saved, ItemStatus.REGISTRAR_SIGNED);
        return saved;
    }

    private void seedItem(Batch batch, ItemStatus targetStatus) {
        TranscriptItem item = TranscriptItem.register(
            "tx-" + UUID.randomUUID(), "doc-" + UUID.randomUUID(),
            INSTITUTION, "REGULAR", "seed/" + UUID.randomUUID() + ".xml");
        item.assign(batch.getId());
        if (targetStatus == ItemStatus.REGISTRAR_SIGNED) {
            item.markRegistrarSigned("seed/reg-signed/" + UUID.randomUUID() + ".xml");
        }
        itemRepository.save(item);
    }
}

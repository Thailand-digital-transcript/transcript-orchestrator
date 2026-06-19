package com.wpanther.transcript.orchestrator.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.transcript.orchestrator.domain.model.Batch;
import com.wpanther.transcript.orchestrator.domain.model.BatchStatus;
import com.wpanther.transcript.orchestrator.domain.model.TranscriptItem;
import com.wpanther.transcript.orchestrator.domain.repository.BatchRepository;
import com.wpanther.transcript.orchestrator.domain.repository.TranscriptItemRepository;
import com.wpanther.transcript.orchestrator.infrastructure.config.StorageProperties;
import com.wpanther.transcript.orchestrator.integration.support.IntegrationTestBase;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the A10 decision + content endpoints at the HTTP layer with a mix
 * of JWT (registrar/dean) and API-key callers.
 *
 * <h3>Mock JWT wiring</h3>
 * The test profile does NOT set {@code KEYCLOAK_ISSUER_URI}, so only the
 * API-key {@code SecurityFilterChain} ({@code apiKeyChain}) is active and no
 * bearer-token filter is configured. To exercise the JWT caller paths
 * ({@code CallerContext.institutionCode()} / {@code gateFromRoles()}, which
 * read a {@link JwtAuthenticationToken}) we therefore cannot rely on
 * {@code SecurityMockMvcRequestPostProcessors#jwt()} alone — that post-processor
 * builds a {@code BearerTokenAuthentication}, not the {@code JwtAuthenticationToken}
 * that {@code CallerContext} instanceof-checks. Instead we construct a real
 * {@link JwtAuthenticationToken} carrying the same claims/authorities the
 * production JWT converter would produce ({@code institution_code},
 * {@code preferred_username}, {@code ROLE_REGISTRAR/DEAN}) and inject it via
 * {@link SecurityMockMvcRequestPostProcessors#authentication}. The post-processor
 * sets the {@code SecurityContextHolder} before the chain runs, so the chain's
 * {@code hasAnyRole("REGISTRAR","DEAN")} rule is satisfied and the controller
 * sees the JWT caller exactly as it would in production.
 *
 * <h3>Post-relay assertion</h3>
 * After {@code POST /decision} returns 202, the {@code OutboxRelayRoute}
 * (5s timer) drains the outbox, publishes the {@code OutboundApprovalCommand}
 * to {@code approval.registrar}, and {@code ApprovalConsumerRoute} re-applies
 * the decision, advancing the batch to {@code REGISTRAR_SIGNING}. The happy-
 * path case polls with Awaitility (30s timeout, 1s interval) for that state.
 */
@AutoConfigureMockMvc
class DecisionEndpointIT extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired BatchRepository batchRepository;
    @Autowired TranscriptItemRepository itemRepository;
    @Autowired StorageProperties storageProperties;
    @Autowired ObjectMapper objectMapper;

    private static S3Client seeder;

    @BeforeAll
    static void initSeeder() {
        seeder = S3Client.builder()
            .endpointOverride(URI.create(MINIO.getS3URL()))
            .forcePathStyle(true)
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
            .build();
    }

    @BeforeEach
    void ensureBucket() {
        try {
            seeder.createBucket(CreateBucketRequest.builder()
                .bucket(storageProperties.getXmlBucket()).build());
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException ignore) {
            // already exists — safe to use
        }
    }

    // ---------- happy path: registrar APPROVE advances to REGISTRAR_SIGNING ----------

    @Test
    void registrarApprove_returns202_andAdvancesToRegistrarSigning() throws Exception {
        // Seed the batch WITH at least one ASSIGNED item. HandleRegistrarApprovalUseCase
        // calls applyItemRejections(items, rejectedIds=[]) which returns true (cancelling
        // the batch) when the item list is EMPTY — allMatch on an empty stream is true.
        // A non-empty item list with no rejected ids keeps the batch healthy.
        Batch batch = seedBatchWithItem("01110", BatchStatus.PENDING_REGISTRAR);

        // 202 with the four response fields.
        mockMvc.perform(post("/api/v1/batches/" + batch.getId() + "/decision")
                .with(jwt("registrar1", "01110", "ROLE_REGISTRAR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(decisionBody("APPROVE", null, null)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.batchId").value(batch.getId().toString()))
            .andExpect(jsonPath("$.decisionId").exists())
            .andExpect(jsonPath("$.status").value("PENDING_REGISTRAR"))
            .andExpect(jsonPath("$.accepted").value(true));

        // Outbox relay (5s) → approval.registrar → HandleRegistrarApprovalUseCase
        // → batch advances to REGISTRAR_SIGNING.
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> {
                Batch current = batchRepository.findById(batch.getId()).orElseThrow();
                // soft check; assert via state for a clear failure message
                org.assertj.core.api.Assertions.assertThat(current.getStatus())
                    .isEqualTo(BatchStatus.REGISTRAR_SIGNING);
            });
    }

    // ---------- 400: REJECT with blank reason ----------

    @Test
    void rejectWithBlankReason_returns400() throws Exception {
        Batch batch = seedBatch("01110", BatchStatus.PENDING_REGISTRAR);
        mockMvc.perform(post("/api/v1/batches/" + batch.getId() + "/decision")
                .with(jwt("registrar1", "01110", "ROLE_REGISTRAR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(decisionBody("REJECT", null, "   ")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    // ---------- 409: registrar APPROVE on a PENDING_DEAN batch ----------

    @Test
    void registrarApprove_onPendingDeanBatch_returns409() throws Exception {
        Batch batch = seedBatch("01110", BatchStatus.PENDING_DEAN);
        mockMvc.perform(post("/api/v1/batches/" + batch.getId() + "/decision")
                .with(jwt("registrar1", "01110", "ROLE_REGISTRAR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(decisionBody("APPROVE", null, null)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").exists());
    }

    // ---------- 404: cross-institution APPROVE ----------

    @Test
    void approve_onForeignInstitutionBatch_returns404() throws Exception {
        Batch batch = seedBatch("99999", BatchStatus.PENDING_REGISTRAR);
        mockMvc.perform(post("/api/v1/batches/" + batch.getId() + "/decision")
                .with(jwt("registrar1", "01110", "ROLE_REGISTRAR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(decisionBody("APPROVE", null, null)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").exists());
    }

    // ---------- 403: X-API-Key caller has no approver role ----------

    @Test
    void apiKeyCaller_decision_returns403() throws Exception {
        Batch batch = seedBatch("01110", BatchStatus.PENDING_REGISTRAR);
        // ROLE_API (set by ApiKeyFilter) lacks ROLE_REGISTRAR/DEAN → Spring
        // Security's hasAnyRole("REGISTRAR","DEAN") rejects the request with 403.
        mockMvc.perform(post("/api/v1/batches/" + batch.getId() + "/decision")
                .header("X-API-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(decisionBody("APPROVE", null, null)))
            .andExpect(status().isForbidden());
    }

    // ---------- 403: JWT registrar role but NO institution_code claim ----------

    @Test
    void jwtRegistrar_withoutInstitutionClaim_returns403() throws Exception {
        Batch batch = seedBatch("01110", BatchStatus.PENDING_REGISTRAR);
        // ROLE_REGISTRAR but no institution_code claim → ApprovalController's
        // gateFromRoles() succeeds (403 not triggered there) but
        // institutionCode() is empty → ForbiddenAccessException → 403.
        mockMvc.perform(post("/api/v1/batches/" + batch.getId() + "/decision")
                .with(jwtNoInstitution("registrar1", "ROLE_REGISTRAR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(decisionBody("APPROVE", null, null)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").exists());
    }

    // ---------- 200 + 404: GET /transcripts/{id}/content ----------

    @Test
    void getContent_returns200_applicationXml() throws Exception {
        String xml = "<tc:Transcript xmlns:tc=\"urn:tc\">hi</tc:Transcript>";
        String key = seedXml(xml);
        TranscriptItem item = seedItem("01110", key);

        // StreamingResponseBody is async: the first perform() returns with
        // asyncStarted, and we must dispatch the async result before asserting
        // on the streamed body. Without asyncDispatch, MockMvcResultMatchers see
        // an empty response buffer (the stream never ran).
        MvcResult result = mockMvc.perform(get("/api/v1/transcripts/" + item.getId() + "/content")
                .with(jwt("registrar1", "01110", "ROLE_REGISTRAR"))
                .accept(MediaType.APPLICATION_XML))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_XML))
            .andExpect(content().string(xml));
    }

    @Test
    void getContent_crossInstitution_returns404() throws Exception {
        String xml = "<tc:Transcript>other</tc:Transcript>";
        String key = seedXml(xml);
        TranscriptItem item = seedItem("99999", key);

        mockMvc.perform(get("/api/v1/transcripts/" + item.getId() + "/content")
                .with(jwt("registrar1", "01110", "ROLE_REGISTRAR"))
                .accept(MediaType.APPLICATION_XML))
            .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    /**
     * Builds a {@link JwtAuthenticationToken} carrying the given username,
     * institution code, and authorities. This matches what
     * {@code SecurityConfig#jwtConverter()} produces from a real Keycloak JWT.
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

    /** Same as {@link #jwt} but WITHOUT the {@code institution_code} claim. */
    private static RequestPostProcessor jwtNoInstitution(
            String username, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
            .map(SimpleGrantedAuthority::new)
            .map(a -> (GrantedAuthority) a)
            .toList();
        Map<String, Object> claims = new HashMap<>();
        claims.put("preferred_username", username);
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
     * Seeds a batch directly via the repository (no Kafka saga needed). For
     * PENDING_DEAN, the registrar-approved transition is applied in-memory.
     */
    private Batch seedBatch(String institutionCode, BatchStatus target) {
        Batch b = Batch.create("decision-it-" + UUID.randomUUID(), institutionCode, "seeder");
        b.applyClose("seeder", Instant.now());
        if (target == BatchStatus.PENDING_DEAN) {
            b.applyRegistrarApproved("seeder", Instant.now());
        }
        return batchRepository.save(b);
    }

    /**
     * Seeds a batch with one ASSIGNED item (so the approval consumer does not
     * cancel the batch — see {@link #registrarApprove_returns202_andAdvancesToRegistrarSigning}).
     * Used by the happy-path test only; the other cases assert on the controller
     * response and never reach the consumer, so they use the lighter {@link #seedBatch}.
     */
    private Batch seedBatchWithItem(String institutionCode, BatchStatus target) {
        Batch b = seedBatch(institutionCode, target);
        String xmlKey = "seed/" + UUID.randomUUID() + ".xml";
        TranscriptItem item = TranscriptItem.register(
            "tx-" + UUID.randomUUID(), "doc-" + UUID.randomUUID(),
            institutionCode, "REGULAR", xmlKey);
        item.assign(b.getId());
        itemRepository.save(item);
        return b;
    }

    private TranscriptItem seedItem(String institutionCode, String storageKey) {
        TranscriptItem item = TranscriptItem.register(
            "tx-" + UUID.randomUUID(), "doc-" + UUID.randomUUID(),
            institutionCode, "REGULAR", storageKey);
        return itemRepository.save(item);
    }

    private String seedXml(String body) {
        String key = "decision-it/" + UUID.randomUUID() + ".xml";
        seeder.putObject(req -> req.bucket(storageProperties.getXmlBucket()).key(key),
            RequestBody.fromString(body, StandardCharsets.UTF_8));
        return key;
    }
}

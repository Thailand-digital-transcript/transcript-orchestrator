package com.wpanther.transcript.orchestrator.integration;

import com.wpanther.transcript.orchestrator.integration.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the dual-auth chain (A9) at the HTTP layer.
 *
 * <p>The test profile does NOT set {@code KEYCLOAK_ISSUER_URI}, so the
 * JWT-aware chain is inactive ({@code IssuerConfiguredCondition} is false)
 * and the API-key-only {@code apiKeyChain} is the only registered
 * {@code SecurityFilterChain}. The two cases below assert:
 * <ol>
 *   <li>no credentials on a protected path → 401
 *       (the fall-through {@code ApiKeyFilter} lets the request continue, but
 *        the API-key chain has no JWT bearer filter to authenticate it, so
 *        {@code anyRequest().authenticated()} rejects it).</li>
 *   <li>a valid {@code X-API-Key: test-key} on the same path → 2xx
 *       ({@code ApiKeyFilter} sets {@code ROLE_API}, satisfying
 *        {@code anyRequest().authenticated()}).</li>
 * </ol>
 *
 * <p><strong>What this suite does NOT exercise (and why):</strong> because
 * {@code KEYCLOAK_ISSUER_URI} is unset, the {@code oauth2ResourceServer().jwt()}
 * bearer filter and {@code SecurityConfig#jwtConverter()} never run in the IT
 * profile. The JWT caller paths (registrar/dean tokens, role mapping, decision
 * authorization) are covered in {@code DecisionEndpointIT} by manually
 * constructing a {@code JwtAuthenticationToken} and injecting it via
 * {@code SecurityMockMvcRequestPostProcessors#authentication(...)} — that
 * bypasses both the bearer filter and the JWT authorities converter, so it
 * does NOT prove the converter maps {@code realm_access.roles} to
 * {@code ROLE_<UPPER>} correctly. That converter logic is pinned separately by
 * {@code SecurityConfigTest} (a focused unit test that calls
 * {@code jwtConverter()} directly, fix I2).
 */
class DualAuthIT extends IntegrationTestBase {

    private static final String PROTECTED_PATH = "/api/v1/batches";

    @Test
    void noCredentialsRejected() {
        ResponseEntity<Void> r = restTemplate.exchange(
                PROTECTED_PATH, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), Void.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void apiKeyAccepted() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-API-Key", "test-key");

        ResponseEntity<Void> r = restTemplate.exchange(
                PROTECTED_PATH, HttpMethod.GET, new HttpEntity<>(h), Void.class);

        // Any 2xx is fine — we are only authenticating the caller here. The
        // happy-path IT covers the body content; this IT only cares that the
        // API-key chain accepts the request instead of returning 401/403.
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
    }
}

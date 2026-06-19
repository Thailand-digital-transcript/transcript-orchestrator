package com.wpanther.transcript.orchestrator.integration;

import com.wpanther.transcript.orchestrator.integration.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JWT-only auth at the HTTP layer. The test profile substitutes a {@code JwtDecoder}
 * (TestJwtConfig) that trusts {@code TestTokens}' keypair, so a locally-minted bearer
 * authenticates against the real resource-server filter.
 */
class AuthIT extends IntegrationTestBase {

    private static final String PROTECTED_PATH = "/api/v1/batches";

    @Test
    void noToken_returns401() {
        ResponseEntity<Void> r = restTemplate.exchange(
            PROTECTED_PATH, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), Void.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void malformedToken_returns401() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth("not-a-real-jwt");
        ResponseEntity<Void> r = restTemplate.exchange(
            PROTECTED_PATH, HttpMethod.GET, new HttpEntity<>(h), Void.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validToken_returns2xx() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(bearerToken());
        ResponseEntity<Void> r = restTemplate.exchange(
            PROTECTED_PATH, HttpMethod.GET, new HttpEntity<>(h), Void.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void actuatorHealth_isOpen() {
        ResponseEntity<String> r = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
    }
}

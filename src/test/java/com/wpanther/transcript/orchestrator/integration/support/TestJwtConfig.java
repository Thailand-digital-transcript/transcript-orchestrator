package com.wpanther.transcript.orchestrator.integration.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Test-profile {@link JwtDecoder} that trusts {@link TestTokens}' keypair, so
 * live-HTTP {@code TestRestTemplate} calls can present locally-minted bearer
 * tokens without a real Keycloak. The production {@code jwtDecoder} bean is
 * {@code @ConditionalOnMissingBean}, so this overrides it in the IT context.
 */
@TestConfiguration
public class TestJwtConfig {

    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TestTokens.publicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtIssuerValidator(TestTokens.ISSUER), new JwtTimestampValidator()));
        return decoder;
    }
}

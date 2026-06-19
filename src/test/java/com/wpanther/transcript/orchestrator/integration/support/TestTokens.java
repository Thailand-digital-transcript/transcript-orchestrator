package com.wpanther.transcript.orchestrator.integration.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** Mints RS256 JWTs for integration tests, signed by a per-JVM test keypair. */
public final class TestTokens {

    /** Must match the orchestrator's expected issuer (application-test.yml keycloak.issuer). */
    public static final String ISSUER = "http://localhost:8080/realms/transcript";

    private static final RSAKey RSA;
    static {
        try {
            RSA = new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (JOSEException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private TestTokens() {}

    public static RSAPublicKey publicKey() {
        try {
            return RSA.toRSAPublicKey();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @param institutionCode nullable; when null the {@code institution_code} claim
     *                        is omitted (unscoped caller).
     */
    public static String bearer(List<String> roles, String institutionCode, String username) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(username)
                .claim("preferred_username", username)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .claim("realm_access", Map.of("roles", roles));
            if (institutionCode != null) {
                claims.claim("institution_code", institutionCode);
            }
            SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(RSA.getKeyID()).build(),
                claims.build());
            jwt.sign(new RSASSASigner(RSA));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("failed to mint test JWT", e);
        }
    }
}

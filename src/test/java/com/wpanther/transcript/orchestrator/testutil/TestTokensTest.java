package com.wpanther.transcript.orchestrator.testutil;

import com.nimbusds.jwt.SignedJWT;
import com.wpanther.transcript.orchestrator.integration.support.TestTokens;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TestTokensTest {

    @Test
    void mintsAParseableTokenWithClaims() throws Exception {
        String token = TestTokens.bearer(List.of("registrar"), "KMUTT", "alice");
        SignedJWT jwt = SignedJWT.parse(token);
        var claims = jwt.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo(TestTokens.ISSUER);
        assertThat(claims.getStringClaim("preferred_username")).isEqualTo("alice");
        assertThat(claims.getStringClaim("institution_code")).isEqualTo("KMUTT");
        assertThat(((java.util.Map<?, ?>) claims.getClaim("realm_access")).get("roles"))
            .isEqualTo(List.of("registrar"));
    }

    @Test
    void omitsInstitutionCodeWhenNull() throws Exception {
        String token = TestTokens.bearer(List.of("registrar"), null, "bob");
        var claims = SignedJWT.parse(token).getJWTClaimsSet();
        assertThat(claims.getClaim("institution_code")).isNull();
    }
}

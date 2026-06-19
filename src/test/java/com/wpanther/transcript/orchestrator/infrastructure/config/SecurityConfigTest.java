package com.wpanther.transcript.orchestrator.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused unit test of {@link SecurityConfig#jwtConverter()} (fix I2).
 *
 * <p>The integration-test profile does NOT set {@code KEYCLOAK_ISSUER_URI}, so
 * only the API-key {@code SecurityFilterChain} ({@code apiKeyChain}) registers
 * and the {@code oauth2ResourceServer().jwt()} bearer filter — which is where
 * this converter actually runs — never executes during the IT suite. The
 * existing {@code DecisionEndpointIT} and {@code DualAuthIT} inject a
 * pre-built {@code JwtAuthenticationToken} via
 * {@code SecurityMockMvcRequestPostProcessors.authentication(...)}, which
 * bypasses the bearer filter AND this converter. A bug in the converter (wrong
 * claim path, wrong {@code ROLE_} prefix, {@code realm_access} shape
 * mismatch) or in {@code IssuerConfiguredCondition} would therefore go
 * undetected.
 *
 * <p>This test constructs {@link Jwt} instances directly and runs them through
 * the real {@link JwtAuthenticationConverter} returned by
 * {@link SecurityConfig#jwtConverter()}, pinning:
 * <ul>
 *   <li>{@code realm_access.roles = ["registrar"]} → exactly {@code ROLE_REGISTRAR}.</li>
 *   <li>{@code realm_access.roles = ["dean"]} → exactly {@code ROLE_DEAN}.</li>
 *   <li>a roles-less Jwt (no {@code realm_access} claim) → no {@code ROLE_} authorities.</li>
 * </ul>
 *
 * <p>{@link SecurityConfig#jwtConverter()} is package-private; this test lives
 * in the same package so it can invoke the real converter without reflection.
 * {@link SecurityConfig} is instantiated directly (its {@code @Value} field is
 * never read by {@code jwtConverter()}, so no Spring context is required).
 */
class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig();

    private Jwt jwtWithRoles(List<String> roles) {
        Map<String, Object> claims = Map.of(
            "sub", "user-1",
            "preferred_username", "alice",
            "institution_code", "01110",
            "realm_access", Map.of("roles", roles));
        return Jwt.withTokenValue("mock-token")
            .header("alg", "none")
            .claims(c -> c.putAll(claims))
            .build();
    }

    private Jwt jwtWithoutRealmAccess() {
        return Jwt.withTokenValue("mock-token")
            .header("alg", "none")
            .claims(c -> {
                c.put("sub", "user-2");
                c.put("preferred_username", "bob");
                c.put("institution_code", "01110");
                // NOTE: no realm_access claim at all.
            })
            .build();
    }

    private static Set<String> authorityNames(Collection<? extends GrantedAuthority> auths) {
        return auths.stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
    }

    @Test
    void registrarRole_mapsToRoleRegistrar() {
        JwtAuthenticationConverter converter = config.jwtConverter();
        var token = converter.convert(jwtWithRoles(List.of("registrar")));
        assertThat(token).isNotNull();
        assertThat(authorityNames(token.getAuthorities())).containsExactly("ROLE_REGISTRAR");
    }

    @Test
    void deanRole_mapsToRoleDean() {
        JwtAuthenticationConverter converter = config.jwtConverter();
        var token = converter.convert(jwtWithRoles(List.of("dean")));
        assertThat(token).isNotNull();
        assertThat(authorityNames(token.getAuthorities())).containsExactly("ROLE_DEAN");
    }

    @Test
    void multipleRoles_allMappedToUppercase() {
        // Belt-and-suspenders: the spec only needs registrar/dean, but the
        // converter uppercases every realm role. Pinning this guards against a
        // future regression where the prefix or casing logic is broken.
        JwtAuthenticationConverter converter = config.jwtConverter();
        var token = converter.convert(jwtWithRoles(List.of("registrar", "dean")));
        assertThat(token).isNotNull();
        assertThat(authorityNames(token.getAuthorities()))
            .containsExactlyInAnyOrder("ROLE_REGISTRAR", "ROLE_DEAN");
    }

    @Test
    void rolesLessJwt_yieldsNoRoleAuthorities() {
        JwtAuthenticationConverter converter = config.jwtConverter();
        var token = converter.convert(jwtWithoutRealmAccess());
        assertThat(token).isNotNull();
        // No realm_access claim → converter produces no authorities at all.
        assertThat(token.getAuthorities()).isEmpty();
    }
}

package com.wpanther.transcript.orchestrator.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * JWT-only security. One {@link SecurityFilterChain}: actuator open, the
 * decision endpoint requires REGISTRAR/DEAN, everything else authenticated.
 *
 * <p>The {@link JwtDecoder} validates a fixed {@code iss} string
 * ({@code keycloak.issuer}) but fetches keys from a separate internal
 * {@code keycloak.jwks-uri}, so the browser-facing issuer can differ from the
 * in-network JWKS host (spec §4.1). It is {@code @ConditionalOnMissingBean} so
 * the test profile can substitute a decoder that trusts a local test keypair.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource cors) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(c -> c.configurationSource(cors))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/batches/*/decision")
                    .hasAnyRole("REGISTRAR", "DEAN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtConverter())));
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean
    JwtDecoder jwtDecoder(@Value("${keycloak.jwks-uri}") String jwksUri,
                          @Value("${keycloak.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtIssuerValidator(issuer), new JwtTimestampValidator()));
        return decoder;
    }

    /**
     * CORS for the browser SPA. Configured via {@code http.cors(...)} +
     * {@link CorsConfigurationSource} so Spring emits {@code Vary: Origin}
     * automatically. Bearer auth (no cookies) → no allow-credentials needed.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.cors.allowed-origins:}") String allowedOrigins) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).toList();
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/api/**", cfg);
        return src;
    }

    /**
     * Maps Keycloak {@code realm_access.roles} to {@code ROLE_<UPPER>}
     * authorities. Package-private so {@code SecurityConfigTest} can call it
     * directly. Unchanged from the previous dual-auth implementation.
     */
    JwtAuthenticationConverter jwtConverter() {
        JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter((Jwt jwt) -> {
            Object realm = jwt.getClaims().get("realm_access");
            Collection<GrantedAuthority> out = new ArrayList<>();
            if (realm instanceof Map<?, ?> m && m.get("roles") instanceof Collection<?> roles) {
                for (Object r : roles) {
                    out.add(new SimpleGrantedAuthority("ROLE_" + r.toString().toUpperCase()));
                }
            }
            return out;
        });
        return conv;
    }
}

package com.wpanther.transcript.orchestrator.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.GenericFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Security configuration for the orchestrator.
 *
 * <p>Two mutually-exclusive {@link SecurityFilterChain} beans are registered,
 * gated by two exact-negation {@link Condition}s on
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}:
 * <ul>
 *   <li>{@link IssuerNotConfiguredCondition} → {@code apiKeyChain} (default, on-prem / test profile).</li>
 *   <li>{@link IssuerConfiguredCondition} → {@code jwtChain} (browser SPA with Keycloak).</li>
 * </ul>
 * Exactly one chain is ever active per profile, which avoids the
 * {@code @ConditionalOnProperty(matchIfMissing=true, havingValue="__never__")} footgun.
 *
 * <p>The {@link ApiKeyFilter} is <em>fall-through</em>: when {@code X-API-Key} is
 * ABSENT it calls {@code chain.doFilter(...)} so the request can reach the JWT
 * bearer filter on {@code jwtChain}. It only returns 401 when the header is
 * present-but-invalid.
 */
@Configuration
public class SecurityConfig {

    @Value("${app.security.api-keys:}")
    private String rawApiKeys;

    private Set<String> keys() {
        return Arrays.stream(rawApiKeys.split(","))
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .collect(Collectors.toSet());
    }

    private void common(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .addFilterBefore(new ApiKeyFilter(keys()), UsernamePasswordAuthenticationFilter.class)
            // Spring Security 6's default AuthenticationEntryPoint is
            // Http403ForbiddenEntryPoint, so a no-credentials request that
            // falls through ApiKeyFilter (anonymous) would otherwise be
            // rejected with 403. The brief requires 401 for no-credentials.
            // The JWT chain overrides this with BearerTokenAuthenticationEntryPoint
            // once oauth2ResourceServer().jwt() is configured.
            .exceptionHandling(e -> e.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/**").permitAll()
                // Spec §4.3: only REGISTRAR / DEAN may decide. API-key callers
                // (ROLE_API) are intentionally NOT allowed here → Spring Security
                // returns a real 403 instead of silently accepting the decision.
                .requestMatchers(HttpMethod.POST, "/api/v1/batches/*/decision")
                    .hasAnyRole("REGISTRAR", "DEAN")
                .anyRequest().authenticated());
    }

    /**
     * API-key-only chain. Active only when NO issuer-uri is configured
     * (absent or blank). This is the test profile and any on-prem deploy
     * without Keycloak.
     */
    @Bean
    @Order(1)
    @Conditional(IssuerNotConfiguredCondition.class)
    public SecurityFilterChain apiKeyChain(HttpSecurity http) throws Exception {
        common(http);
        return http.build();
    }

    /**
     * JWT-aware chain. Active only when
     * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} is present
     * AND non-blank. Adds an OAuth2 resource server with a converter that maps
     * Keycloak {@code realm_access.roles} entries to {@code ROLE_<UPPER>}
     * authorities (e.g. {@code registrar} → {@code ROLE_REGISTRAR}).
     */
    @Bean
    @Order(2)
    @Conditional(IssuerConfiguredCondition.class)
    public SecurityFilterChain jwtChain(HttpSecurity http) throws Exception {
        common(http);
        http.oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtConverter())));
        return http.build();
    }

    /**
     * Maps Keycloak {@code realm_access.roles} entries to
     * {@code ROLE_<UPPER>} authorities (e.g. {@code registrar} →
     * {@code ROLE_REGISTRAR}, {@code dean} → {@code ROLE_DEAN}). Returns no
     * {@code ROLE_} authorities when the {@code realm_access} claim is absent
     * or malformed.
     *
     * <p>Package-private so {@code SecurityConfigTest} (same package) can unit
     * test the converter logic directly — the integration test profile does
     * not set {@code KEYCLOAK_ISSUER_URI}, so the JWT bearer filter and this
     * converter are never exercised by the IT suite.
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

    /**
     * Fall-through API-key filter. When {@code X-API-Key} is ABSENT, the filter
     * calls {@code chain.doFilter(...)} so the request can reach the JWT bearer
     * filter on {@code jwtChain}. It returns 401 ONLY when the header is
     * present-but-invalid, and short-circuits {@code /actuator/**} before any
     * header check to match the actuator {@code permitAll()} rule.
     */
    static class ApiKeyFilter extends GenericFilter {
        private final Set<String> keys;

        ApiKeyFilter(Set<String> k) { this.keys = k; }

        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest r = (HttpServletRequest) req;
            // N4 note: /actuator/** short-circuit matches the permitAll() in
            // common(). Avoids the X-API-Key check for scrape-friendly endpoints
            // like /actuator/prometheus.
            if (r.getRequestURI().startsWith("/actuator")) {
                chain.doFilter(req, res);
                return;
            }
            String k = r.getHeader("X-API-Key");
            if (k == null) {
                // Fall through: a JWT-only browser request must reach the bearer
                // filter on jwtChain. Returning 401 here would break Phase B.
                chain.doFilter(req, res);
                return;
            }
            if (!keys.contains(k)) {
                ((HttpServletResponse) res).sendError(401);
                return;
            }
            // A valid key authenticates the caller as ROLE_API. Without this the
            // request continues as anonymous and `anyRequest().authenticated()`
            // rejects it with 403, so every valid-key request would fail.
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "api-key", null, List.of(new SimpleGrantedAuthority("ROLE_API"))));
            chain.doFilter(req, res);
        }
    }

    private static final String ISSUER_URI =
            "spring.security.oauth2.resourceserver.jwt.issuer-uri";

    /** True iff {@code issuer-uri} is present AND non-blank. */
    static class IssuerConfiguredCondition implements Condition {
        @Override
        public boolean matches(ConditionContext ctx, AnnotatedTypeMetadata md) {
            return StringUtils.hasText(ctx.getEnvironment().getProperty(ISSUER_URI));
        }
    }

    /** Exact negation of {@link IssuerConfiguredCondition}. */
    static class IssuerNotConfiguredCondition implements Condition {
        @Override
        public boolean matches(ConditionContext ctx, AnnotatedTypeMetadata md) {
            return !StringUtils.hasText(ctx.getEnvironment().getProperty(ISSUER_URI));
        }
    }
}

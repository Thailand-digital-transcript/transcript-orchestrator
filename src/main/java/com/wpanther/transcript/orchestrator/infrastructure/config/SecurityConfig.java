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
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class SecurityConfig {

    @Value("${app.security.api-keys:}")
    private String rawApiKeys;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        Set<String> keys = Arrays.stream(rawApiKeys.split(","))
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .collect(Collectors.toSet());
        http.csrf(AbstractHttpConfigurer::disable)
            .addFilterBefore(new ApiKeyFilter(keys), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(a -> a
                // N4 note: /actuator/** is also short-circuited inside
                // ApiKeyFilter below. Both are intentional: this permitAll()
                // lets Spring Security skip auth checks, the short-circuit
                // avoids the X-API-Key check for scrape-friendly endpoints
                // like /actuator/prometheus.
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated());
        return http.build();
    }

    static class ApiKeyFilter extends GenericFilter {
        private final Set<String> keys;

        ApiKeyFilter(Set<String> k) { this.keys = k; }

        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest r = (HttpServletRequest) req;
            if (r.getRequestURI().startsWith("/actuator")) {
                chain.doFilter(req, res);
                return;
            }
            String k = r.getHeader("X-API-Key");
            if (k == null || !keys.contains(k)) {
                ((HttpServletResponse) res).sendError(401);
                return;
            }
            // A valid key authenticates the caller. Without this the request
            // continues as anonymous and `anyRequest().authenticated()` rejects
            // it with 403, so every valid-key request would otherwise fail.
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "api-key", null, List.of(new SimpleGrantedAuthority("ROLE_API"))));
            chain.doFilter(req, res);
        }
    }
}

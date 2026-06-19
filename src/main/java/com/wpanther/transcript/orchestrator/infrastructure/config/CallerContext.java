package com.wpanther.transcript.orchestrator.infrastructure.config;

import com.wpanther.transcript.orchestrator.domain.model.BatchStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Helper for reading the current caller's identity from
 * {@link SecurityContextHolder}. Handles both authentication modes registered
 * by {@link SecurityConfig}:
 *
 * <ul>
 *   <li>API-key callers — principal name {@code "api-key"} (no JWT, no scope).
 *       {@link #isApiKey()} returns true, {@link #institutionCode()} is empty.</li>
 *   <li>JWT (Keycloak) callers — a {@link JwtAuthenticationToken} carrying a
 *       {@link Jwt} with {@code preferred_username}, {@code institution_code},
 *       and {@code realm_access.roles} claims mapped to {@code ROLE_<UPPER>}
 *       authorities by {@code SecurityConfig#jwtConverter()}.</li>
 * </ul>
 *
 * <p>Decision endpoints (Phase B) use {@link #gateFromRoles()} to map the
 * caller's role to the human-gate batch status they are allowed to advance.
 */
@Component
public class CallerContext {

    /** True for {@code api-key} principals registered by {@code ApiKeyFilter}. */
    public boolean isApiKey() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && "api-key".equals(a.getName());
    }

    /** Caller username — {@code preferred_username} (JWT) or principal name. */
    public String username() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a instanceof JwtAuthenticationToken jt) {
            Jwt jwt = jt.getToken();
            Object u = jwt.getClaims().getOrDefault("preferred_username", jwt.getSubject());
            return String.valueOf(u);
        }
        return a == null ? "unknown" : a.getName();
    }

    /**
     * Institution code from the {@code institution_code} JWT claim. Empty for
     * API-key callers (unscoped).
     */
    public Optional<String> institutionCode() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a instanceof JwtAuthenticationToken jt) {
            Object c = jt.getToken().getClaims().get("institution_code");
            return c == null ? Optional.empty() : Optional.of(String.valueOf(c));
        }
        return Optional.empty();
    }

    /**
     * Map the caller's realm role to the human-gate batch status they may
     * advance: {@code REGISTRAR} → {@code PENDING_REGISTRAR},
     * {@code DEAN} → {@code PENDING_DEAN}. Empty for API-key callers or
     * roles that are not human-gate owners.
     *
     * <p>Limitation: a caller holding BOTH roles resolves to the REGISTRAR gate
     * (checked first); they cannot act on the dean gate through this single-gate
     * derivation (a dean decision on such a token would 409 on the wrong gate).
     * Dual-role users are out of scope for Phase A; if needed, add an explicit
     * {@code ?gate=} request override rather than changing this precedence.
     */
    public Optional<BatchStatus> gateFromRoles() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) {
            return Optional.empty();
        }
        boolean reg = a.getAuthorities().stream()
                .anyMatch(x -> "ROLE_REGISTRAR".equals(x.getAuthority()));
        boolean dean = a.getAuthorities().stream()
                .anyMatch(x -> "ROLE_DEAN".equals(x.getAuthority()));
        if (reg) {
            return Optional.of(BatchStatus.PENDING_REGISTRAR);
        }
        if (dean) {
            return Optional.of(BatchStatus.PENDING_DEAN);
        }
        return Optional.empty();
    }
}

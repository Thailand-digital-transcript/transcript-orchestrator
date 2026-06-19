package com.wpanther.transcript.orchestrator.infrastructure.adapter.in.web;

import com.wpanther.transcript.orchestrator.application.dto.command.SubmitDecisionCommand;
import com.wpanther.transcript.orchestrator.application.usecase.SubmitBatchDecisionUseCase;
import com.wpanther.transcript.orchestrator.domain.exception.BatchNotFoundException;
import com.wpanther.transcript.orchestrator.domain.exception.DecisionValidationException;
import com.wpanther.transcript.orchestrator.domain.exception.ForbiddenAccessException;
import com.wpanther.transcript.orchestrator.domain.exception.InstitutionMismatchException;
import com.wpanther.transcript.orchestrator.domain.exception.InvalidBatchStateException;
import com.wpanther.transcript.orchestrator.domain.model.BatchStatus;
import com.wpanther.transcript.orchestrator.infrastructure.adapter.in.web.dto.DecisionRequest;
import com.wpanther.transcript.orchestrator.infrastructure.config.CallerContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Approval decision endpoint (A10). Accepts a registrar/dean APPROVE|REJECT
 * decision, enforces that the caller carries an approver role AND an
 * {@code institution_code} claim, then delegates to
 * {@link SubmitBatchDecisionUseCase}, which writes the decision to the
 * pending-decision table and publishes an {@code OutboundApprovalCommand}
 * via the transactional outbox. The state machine advances asynchronously
 * once the outbox relay drains and the {@code ApprovalConsumerRoute}
 * re-applies the decision, hence the {@code 202 Accepted} response.
 *
 * <p>Exception → status mapping (spec §4.4 / §7):
 * <ul>
 *   <li>{@link DecisionValidationException} → 400 (malformed decision body).</li>
 *   <li>{@link ForbiddenAccessException} → 403 (missing role or institution claim).</li>
 *   <li>{@link BatchNotFoundException} and {@link InstitutionMismatchException} →
 *       404 (privacy: cross-institution is indistinguishable from not-found).</li>
 *   <li>{@link InvalidBatchStateException} → 409 (wrong gate / already advanced).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/batches")
@RequiredArgsConstructor
public class ApprovalController {

    private final SubmitBatchDecisionUseCase submitDecision;
    private final CallerContext caller;

    @PostMapping("/{id}/decision")
    public ResponseEntity<Map<String, Object>> decide(@PathVariable UUID id,
            @RequestBody DecisionRequest body) {
        // A missing role/claim is a misconfigured-but-authenticated principal → 403,
        // NOT a 400 on the request body. Spring Security's SecurityFilterChain has
        // already enforced that only ROLE_REGISTRAR / ROLE_DEAN reach this method;
        // re-checking here keeps the use case decoupled from HTTP auth and gives a
        // clear 403 (rather than a downstream NPE) when a JWT lacks the institution
        // claim.
        BatchStatus gate = caller.gateFromRoles()
            .orElseThrow(() -> new ForbiddenAccessException(
                "Caller has no approver role (REGISTRAR/DEAN)"));
        String institutionCode = caller.institutionCode()
            .orElseThrow(() -> new ForbiddenAccessException(
                "Caller JWT has no institution_code claim"));

        UUID decisionId = submitDecision.submit(new SubmitDecisionCommand(
            id, gate, caller.username(), institutionCode, body.decision(),
            body.rejectedDocumentIds(), body.rejectionReason()));

        return ResponseEntity.accepted().body(Map.of(
            "batchId", id,
            "decisionId", decisionId,
            "status", gate,
            "accepted", true));
    }

    @ExceptionHandler(DecisionValidationException.class)
    ResponseEntity<Map<String, String>> badRequest(DecisionValidationException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenAccessException.class)
    ResponseEntity<Map<String, String>> forbidden(ForbiddenAccessException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    // Privacy: BatchNotFoundException and InstitutionMismatchException both map
    // to 404 so a cross-institution caller cannot distinguish "not yours" from
    // "does not exist". This OVERRIDES BatchController's 403 handler for
    // InstitutionMismatchException on the decision route, because each
    // @RestController applies its own @ExceptionHandler first.
    //
    // The body is NORMALIZED: both cases emit the identical
    // {"error": "Batch not found: <path-id>"} string. The batch id is read from
    // the request's URI template variable (not from the exception message), so
    // even a future code path that throws InstitutionMismatchException with a
    // different internal message shape cannot leak the caller's institution or
    // confirm existence. (As of the I1 fix, SubmitBatchDecisionUseCase throws
    // BatchNotFoundException directly on the cross-institution path, so the two
    // cases are indistinguishable at every layer.)
    @ExceptionHandler({BatchNotFoundException.class, InstitutionMismatchException.class})
    ResponseEntity<Map<String, String>> notFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "Batch not found: " + batchIdFromPath()));
    }

    /**
     * Reads the {@code id} path variable from the current request's URI
     * template variables. Used by {@link #notFound} so both 404 cases (missing
     * batch and cross-institution) emit an identical body that contains only
     * the id the caller already supplied — never the caller's institution or
     * any hint about why the request was rejected.
     */
    private String batchIdFromPath() {
        var attrs = org.springframework.web.context.request.RequestContextHolder
            .getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
            Object raw = sra.getRequest().getAttribute(
                org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            if (raw instanceof Map<?, ?> vars && vars.get("id") != null) {
                return vars.get("id").toString();
            }
        }
        return "unknown";
    }

    @ExceptionHandler(InvalidBatchStateException.class)
    ResponseEntity<Map<String, String>> conflict(InvalidBatchStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}

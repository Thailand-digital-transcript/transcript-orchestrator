package com.wpanther.transcript.orchestrator.application.dto.command;

import com.wpanther.transcript.orchestrator.domain.model.BatchStatus;
import java.util.List;
import java.util.UUID;

/** Caller context resolved from the JWT plus the request body. */
public record SubmitDecisionCommand(
        UUID batchId,
        BatchStatus callerGate,     // PENDING_REGISTRAR or PENDING_DEAN (from role)
        String approvedBy,          // JWT preferred_username/sub
        String institutionCode,     // JWT institution_code claim
        String decision,            // APPROVE | REJECT
        List<String> rejectedDocumentIds,
        String rejectionReason
) {}

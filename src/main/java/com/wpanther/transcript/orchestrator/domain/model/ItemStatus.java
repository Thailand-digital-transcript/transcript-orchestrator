package com.wpanther.transcript.orchestrator.domain.model;
public enum ItemStatus {
    REGISTERED, ASSIGNED, REGISTRAR_SIGNED, DEAN_SIGNED, SEALED,
    PDF_RENDERED, PDF_SIGNED, REJECTED, FAILED;
    public boolean isTerminal() { return this == REJECTED || this == FAILED; }
}
